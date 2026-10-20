"""Validate the frozen v0.8.4 build artifacts; no backend or Telegram actions."""
import hashlib, json, os, pathlib, subprocess, time, urllib.request, zipfile
import xml.etree.ElementTree as ET

REPO = "susuy0/Yacd-meta"
RUN = 39373491768
SOURCE = "55eda3173f0f438aef5d3e52f62da1abbdd37c6e"
TOKEN = os.environ["GH_TOKEN"]
ROOT = pathlib.Path("verification")
ROOT.mkdir(exist_ok=True)
def api(path):
    req = urllib.request.Request("https://api.github.com/repos/" + REPO + path,
        headers={"Authorization": "Bearer " + TOKEN, "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)
names = {"apk": "VineChat-Futoon-v0.8.4-native-development-no-fcm", "native_reports": "Futoon-v0.8.4-native-business-checks"}
deadline = time.monotonic() + 5400
while True:
    run = api("/actions/runs/" + str(RUN))
    assert run["head_sha"] == SOURCE
    listed = api("/actions/runs/" + str(RUN) + "/artifacts")["artifacts"]
    found = {kind: next((a for a in listed if a["name"] == name and not a["expired"]), None) for kind, name in names.items()}
    if all(found.values()):
        break
    assert run["conclusion"] not in ("failure", "cancelled", "timed_out"), run["conclusion"]
    assert time.monotonic() < deadline, "Timed out waiting for APK artifact"
    time.sleep(20)
artifacts = {}
archives = {}
for kind, item in found.items():
    target = ROOT / (kind + ".zip")
    subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error", "--retry", "3",
        "--max-time", "300", "-H", "Authorization: Bearer " + TOKEN, "-H", "Accept: application/vnd.github+json",
        "-o", str(target), item["archive_download_url"]], check=True)
    content = target.read_bytes()
    digest = hashlib.sha256(content).hexdigest()
    assert len(content) == item["size_in_bytes"]
    assert item["digest"] == "sha256:" + digest
    archive = zipfile.ZipFile(target)
    assert archive.testzip() is None
    archives[kind] = archive
    artifacts[kind] = dict(id=item["id"], name=item["name"], zip_bytes=len(content), zip_sha256=digest)
reports = archives["native_reports"]
suites = [ET.fromstring(reports.read(n)) for n in reports.namelist() if n.startswith("test-results/") and n.endswith(".xml") and pathlib.PurePosixPath(n).name.startswith("TEST-")]
assert suites
totals = {key: sum(int(s.attrib.get(key, "0")) for s in suites) for key in ("tests", "failures", "errors", "skipped")}
assert totals == dict(tests=303, failures=0, errors=0, skipped=0), totals
screenshots = len([n for n in reports.namelist() if n.startswith("ui-screenshots/") and n.endswith(".png")])
assert screenshots == 43, screenshots
lint_names = [n for n in reports.namelist() if n.endswith("lint-results-debug.xml")]
assert len(lint_names) == 1, lint_names
lint = ET.fromstring(reports.read(lint_names[0]))
lint_counts = {severity: len([i for i in lint.findall("issue") if i.attrib.get("severity") == severity]) for severity in ("Error", "Fatal", "Warning", "Information")}
assert lint_counts["Error"] == lint_counts["Fatal"] == 0, lint_counts
totals.update(suites=len(suites), screenshots=screenshots, lint=lint_counts)
apk_archive = archives["apk"]
checksums = apk_archive.read("SHA256SUMS").decode().splitlines()
verified = []
for line in checksums:
    digest, filename = line.split(maxsplit=1)
    filename = filename.lstrip("*")
    assert filename in ("VineChat-Futoon-v0.8.4-native-test.apk", "mapping.txt"), filename
    assert hashlib.sha256(apk_archive.read(filename)).hexdigest() == digest
    verified.append(filename)
assert set(verified) == {"VineChat-Futoon-v0.8.4-native-test.apk", "mapping.txt"}
apk_path = ROOT / "SMS-Futoon-v0.8.4-arm64.apk"
apk_path.write_bytes(apk_archive.read("VineChat-Futoon-v0.8.4-native-test.apk"))
result = subprocess.check_output(["python3", "tools/verify_release.py", str(apk_path)], text=True)
identity = json.loads(result)
assert identity["native_core_bytes"] == 9893208, identity["native_core_bytes"]
jobs = api("/actions/runs/" + str(RUN) + "/jobs")["jobs"]
run = api("/actions/runs/" + str(RUN))
record = dict(source_commit=SOURCE, workflow_run=RUN, workflow_status=run["status"], workflow_conclusion=run["conclusion"],
    native_tests=totals, apk=identity, ci_sha256sums="APK and R8 mapping verified", artifacts=artifacts,
    jobs={j["name"]: dict(id=j["id"], status=j["status"], conclusion=j["conclusion"]) for j in jobs},
    limitations="Robolectric and controlled synthetic transport; Android 17 device, IME, live backend and FCM delivery not tested.")
(ROOT / "VERIFIED.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n")
for archive in archives.values():
    archive.close()
for kind in archives:
    (ROOT / (kind + ".zip")).unlink()
print("VERIFICATION_RESULT_START")
print(json.dumps(record, ensure_ascii=False, indent=2))
print("VERIFICATION_RESULT_END")
