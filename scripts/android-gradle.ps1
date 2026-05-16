param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

function Get-JavaMajorVersion {
    param(
        [string]$JavaHome
    )

    $releaseFile = Join-Path $JavaHome "release"
    if (Test-Path $releaseFile) {
        $releaseContent = Get-Content $releaseFile
        $javaVersionLine = $releaseContent | Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
        if ($javaVersionLine -match '"(?<major>\d+)(\.\d+)?') {
            return [int]$Matches.major
        }
    }

    return $null
}

$preferredJavaHomes = @(
    $env:JAVA_HOME,
    "D:\android_studio\jbr",
    (Join-Path ${env:ProgramFiles} "Android\Android Studio\jbr"),
    (Join-Path ${env:LOCALAPPDATA} "Programs\Android Studio\jbr")
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

$selectedJavaHome = $null

foreach ($candidate in $preferredJavaHomes) {
    $javaExecutable = Join-Path $candidate "bin\java.exe"
    if (!(Test-Path $javaExecutable)) {
        continue
    }

    $majorVersion = Get-JavaMajorVersion -JavaHome $candidate
    if ($majorVersion -and $majorVersion -le 21) {
        $selectedJavaHome = $candidate
        break
    }
}

if ($selectedJavaHome) {
    $env:JAVA_HOME = $selectedJavaHome
    $env:Path = (Join-Path $selectedJavaHome "bin") + ";" + $env:Path
    Write-Host "Using JAVA_HOME=$selectedJavaHome"
} else {
    Write-Warning "No compatible Android JBR/JDK (<= 21) was found. Gradle will use the current Java runtime."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = Join-Path $repoRoot "android-client\gradlew.bat"
$androidProjectDir = Join-Path $repoRoot "android-client"

if (!(Test-Path $gradleWrapper)) {
    throw "Gradle wrapper not found: $gradleWrapper"
}

& $gradleWrapper -p $androidProjectDir @GradleArgs
exit $LASTEXITCODE
