#!/usr/bin/env python3
"""通过 GitHub REST API 发布仓库（本机 github.com:443 的 git 协议不通，只有 api.github.com 可达）。
用 curl 发请求，避免 Python 自带证书链问题。"""
import base64, json, os, subprocess, sys

TOKEN = os.environ.get("GITHUB_TOKEN", "").strip()
if not TOKEN:
    sys.exit("缺少 GITHUB_TOKEN")

REPO_NAME = os.environ.get("REPO", "prom-studyrank")
PRIVATE = os.environ.get("PRIVATE", "false").lower() == "true"
DESC = os.environ.get("DESC", "Prom · 学习段位 —— 281 KB 的安卓学习打卡应用")
BRANCH = os.environ.get("BRANCH", "main")
ROOT = os.path.dirname(os.path.abspath(__file__))
API = "https://api.github.com"


def call(method, path, payload=None, allow=(200, 201, 204)):
    cmd = ["curl", "-sS", "-m", "90", "-X", method,
           "-H", f"Authorization: Bearer {TOKEN}",
           "-H", "Accept: application/vnd.github+json",
           "-H", "X-GitHub-Api-Version: 2022-11-28",
           "-H", "User-Agent: prom-publisher",
           "-w", "\n%{http_code}"]
    if payload is not None:
        cmd += ["-H", "Content-Type: application/json", "-d", json.dumps(payload)]
    cmd.append(API + path)
    out = subprocess.run(cmd, capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(f"curl 失败：{out.stderr[:300]}")
    body, _, code = out.stdout.rpartition("\n")
    code = int(code.strip() or 0)
    try:
        data = json.loads(body) if body.strip() else {}
    except json.JSONDecodeError:
        data = {"raw": body}
    if code not in allow:
        sys.exit(f"HTTP {code} {method} {path}\n{str(data)[:600]}")
    return code, data


st, me = call("GET", "/user")
login = me["login"]
print(f"✓ 已认证：{login}")

st, repo = call("GET", f"/repos/{login}/{REPO_NAME}", allow=(200, 404))
if st == 404:
    st, repo = call("POST", "/user/repos", {
        "name": REPO_NAME, "private": PRIVATE, "description": DESC,
        "auto_init": False, "has_issues": True, "has_wiki": False,
    })
    print(f"✓ 已新建仓库：{login}/{REPO_NAME}（{'私有' if PRIVATE else '公开'}）")
else:
    print(f"✓ 仓库已存在：{login}/{REPO_NAME}")

files = [p for p in subprocess.run(["git", "ls-files", "-z"], cwd=ROOT,
                                   capture_output=True).stdout.decode().split("\0") if p]

# 空仓库无法创建 blob：先用 Contents API 打一个初始提交
st, _probe = call("GET", f"/repos/{login}/{REPO_NAME}/contents/README.md", allow=(200, 404))
if st == 404:
    seed = base64.b64encode(b"# Prom\n").decode()
    call("PUT", f"/repos/{login}/{REPO_NAME}/contents/README.md", {
        "message": "chore: 初始化仓库", "content": seed, "branch": BRANCH,
    })
    print("✓ 已创建初始提交（占位 README）")
    st, ref = call("GET", f"/repos/{login}/{REPO_NAME}/git/ref/heads/{BRANCH}")
    parent_sha = ref["object"]["sha"]
else:
    st, ref = call("GET", f"/repos/{login}/{REPO_NAME}/git/ref/heads/{BRANCH}")
    parent_sha = ref["object"]["sha"]
print(f"✓ 待上传 {len(files)} 个文件")

tree = []
for i, rel in enumerate(files, 1):
    with open(os.path.join(ROOT, rel), "rb") as f:
        content = base64.b64encode(f.read()).decode()
    st, blob = call("POST", f"/repos/{login}/{REPO_NAME}/git/blobs",
                    {"content": content, "encoding": "base64"})
    tree.append({"path": rel, "mode": "100644", "type": "blob", "sha": blob["sha"]})
    if i % 8 == 0 or i == len(files):
        print(f"  · {i}/{len(files)}")

msg = subprocess.run(["git", "log", "-1", "--pretty=%B"], cwd=ROOT,
                     capture_output=True, text=True).stdout.strip() or "Prom 12.8"
st, t = call("POST", f"/repos/{login}/{REPO_NAME}/git/trees", {"tree": tree})
st, c = call("POST", f"/repos/{login}/{REPO_NAME}/git/commits",
             {"message": msg, "tree": t["sha"], "parents": [parent_sha]})
st, r = call("POST", f"/repos/{login}/{REPO_NAME}/git/refs",
             {"ref": f"refs/heads/{BRANCH}", "sha": c["sha"]}, allow=(200, 201, 422))
if st == 422:
    st, r = call("PATCH", f"/repos/{login}/{REPO_NAME}/git/refs/heads/{BRANCH}",
                 {"sha": c["sha"], "force": True})
print(f"✓ 已推送 {len(files)} 个文件 → {BRANCH}")
print(f"\n仓库地址：https://github.com/{login}/{REPO_NAME}")
