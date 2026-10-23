# Renders the Play Store icon and feature graphics from src/ with headless Chrome.
# Run from any folder:  powershell -ExecutionPolicy Bypass -File docs\play-store\graphics\render.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$here = $PSScriptRoot
$src = Join-Path $here 'src'

function Render($url, $width, $height, $out) {
    # Chrome reports progress on stderr; don't let PowerShell treat that as a failure.
    $ErrorActionPreference = 'Continue'
    & $chrome --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 `
        "--window-size=$width,$height" "--screenshot=$out" $url 2>&1 | Out-Null
    if (-not (Test-Path $out)) { throw "Chrome did not write $out" }
}

# Rewrites a PNG in the given pixel format. Play wants the icon as 32-bit PNG (with alpha)
# and the feature graphic as 24-bit PNG (no alpha); Chrome's choice depends on the content.
function SetPixelFormat($path, $format) {
    $image = [System.Drawing.Image]::FromFile($path)
    $bitmap = New-Object System.Drawing.Bitmap($image.Width, $image.Height, $format)
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.DrawImage($image, 0, 0, $image.Width, $image.Height)
    $g.Dispose(); $image.Dispose()
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

$srcUrl = 'file:///' + ($src -replace '\\', '/')

$icon = Join-Path $here 'icon-512.png'
Render "$srcUrl/icon.svg" 512 512 $icon
SetPixelFormat $icon ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
foreach ($lang in 'en', 'it') {
    $out = Join-Path $here "feature-graphic-$lang.png"
    Render "$srcUrl/feature-graphic.html#$lang" 1024 500 $out
    SetPixelFormat $out ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
}
Get-ChildItem $here -Filter *.png | ForEach-Object {
    $i = [System.Drawing.Image]::FromFile($_.FullName)
    '{0}: {1}x{2}, {3}, {4:N0} KB' -f $_.Name, $i.Width, $i.Height, $i.PixelFormat, ($_.Length / 1KB)
    $i.Dispose()
}
