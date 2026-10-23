#!/usr/bin/env python3
"""Render native screens in an isolated copy, without adding test plugins to the app."""

import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile


ANDROID = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ANDROID / "verification/screens/native")
    parser.add_argument("--work-dir", type=Path, help="Keep the isolated project for inspection.")
    args = parser.parse_args()
    output = args.output.resolve()
    temporary = tempfile.TemporaryDirectory(prefix="pingpaisa-visual-") if args.work_dir is None else None
    work = Path(temporary.name) if temporary else args.work_dir.resolve()
    project = work / "project"
    if project.exists():
        raise SystemExit(f"Refusing to overwrite an existing verification project: {project}")
    shutil.copytree(ANDROID, project, ignore=shutil.ignore_patterns(
        ".gradle", ".idea", "build", "verification", "local.properties"
    ))
    # Preserve a developer's SDK path only inside this temporary project.
    if (ANDROID / "local.properties").is_file():
        shutil.copy2(ANDROID / "local.properties", project / "local.properties")
    root = project / "build.gradle.kts"
    root.write_text("""buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath("app.cash.paparazzi:paparazzi-gradle-plugin:2.0.0-alpha01") }
}
""" + root.read_text())
    app = project / "app/build.gradle.kts"
    app.write_text(app.read_text().replace(
        "android {", 'apply(plugin = "app.cash.paparazzi")\n\nandroid {', 1
    ).replace(
        "buildFeatures { compose = true }",
        "buildFeatures { compose = true }\n    testOptions { unitTests.isIncludeAndroidResources = true }"
    ) + """
dependencies {
    testImplementation("net.bytebuddy:byte-buddy:1.17.5")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.17.5")
}
""")
    tests = project / "app/src/test/java/com/pingpaisa/app"
    tests.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ANDROID / "verification/AllScreensTest.kt", tests / "AllScreensTest.kt")
    shutil.copy2(ANDROID / "verification/XmlParserCompatibility.java", tests / "XmlParserCompatibility.java")
    try:
        subprocess.run([
            str(project / "gradlew"), ":app:recordPaparazziDebug", "--no-daemon",
            "--console", "plain", "--max-workers=1",
            "-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8",
            "-Pkotlin.compiler.execution.strategy=in-process"
        ], cwd=project, check=True)
        images = project / "app/src/test/snapshots/images"
        output.mkdir(parents=True, exist_ok=True)
        for image in images.glob("*.png"):
            shutil.copy2(image, output / image.name)
        reports = project / "app/build/test-results/testDebugUnitTest"
        for report in reports.glob("TEST-*.xml"):
            shutil.copy2(report, output / report.name)
        print(f"Native render evidence: {output}")
        print("Rendering success is not a pixel-parity assertion; compare against the references.")
    finally:
        if temporary:
            temporary.cleanup()


if __name__ == "__main__":
    main()
