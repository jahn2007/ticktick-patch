"""Check modern Xposed registration in the final APK using only Python's stdlib."""

import sys
from zipfile import ZipFile


def verify(path):
    root = "META-INF/xposed/"
    with ZipFile(path) as apk:
        entries = apk.read(root + "java_init.list").decode("utf-8").splitlines()
        assert entries == ["io.github.ticktickpatch.TickTickModule"], entries
        scope = set(apk.read(root + "scope.list").decode("utf-8").splitlines())
        assert scope == {"com.ticktick.task", "cn.ticktick.task"}, scope
        properties = dict(
            line.split("=", 1)
            for line in apk.read(root + "module.prop").decode("utf-8").splitlines()
            if line and not line.startswith("#")
        )
        assert properties["minApiVersion"] == "102", properties
        assert properties["targetApiVersion"] == "102", properties
        assert properties["staticScope"] == "true", properties
        assert "AndroidManifest.xml" in apk.namelist()
        dex_files = [name for name in apk.namelist() if name.endswith(".dex")]
        assert dex_files, "APK has no DEX code"
        assert any(b"Lio/github/ticktickpatch/TickTickModule;" in apk.read(name)
                   for name in dex_files), "Module entry missing from DEX"
    print("Verified API 102 registration, scope and module entry:", path)


if __name__ == "__main__":
    verify(sys.argv[1])
