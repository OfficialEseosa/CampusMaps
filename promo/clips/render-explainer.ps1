# Renders the five "How CampusMaps works" chapters as separate transparent clips in 3:2 and 3:4.
# Run from anywhere: powershell -File promo/clips/render-explainer.ps1   (needs the deck server on :8770)
param([int[]]$Chapters = @(0,1,2,3,4))
$deck = Join-Path $PSScriptRoot "..\deck"
$out = Join-Path $PSScriptRoot "out\chapters"
$cap = "C:\Users\rapha\AppData\Local\Temp\claude\cap\chapters"
New-Item -ItemType Directory -Force $out | Out-Null
$names = @("1-building-graph", "2-dijkstra", "3-finding-you", "4-arrows", "5-hands-free")
$sizes = @{ "3:2" = @(2160, 1440); "3:4" = @(1620, 2160) }

foreach ($c in $Chapters) {
  foreach ($a in $sizes.Keys) {
    $w, $h = $sizes[$a]
    $tag = $a.Replace(":", "x")
    $frames = Join-Path $cap "$c-$tag"
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("{`"chapter`":$c,`"aspect`":`"$a`"}"))
    $O = Join-Path $out "HowItWorks-$($names[$c])-$tag"
    if (Test-Path "$O-preview.mp4") { Write-Host "skip chapter $c $a (done)"; continue }
    Write-Host "== chapter $c $a"
    Push-Location $deck
    node capture.js explainer 7.5 $frames --fps=30 --warm=0 --w=$w --h=$h --b64=$b64 --pre="window.__handle.play()" 2>&1 | Select-String "done|pageerror|error"
    Pop-Location
    $F = Join-Path $frames "f_%05d.png"
    $O = Join-Path $out "HowItWorks-$($names[$c])-$tag"
    ffmpeg -v error -y -threads 2 -framerate 30 -i $F -c:v libvpx-vp9 -pix_fmt yuva420p -auto-alt-ref 0 -crf 20 -b:v 0 "$O-alpha.webm"
    ffmpeg -v error -y -threads 2 -f lavfi -i "color=0x00FF00:s=${w}x${h}:r=30" -framerate 30 -i $F -filter_complex "[0][1]overlay=shortest=1" -c:v libx264 -crf 16 -pix_fmt yuv420p "$O-greenscreen.mp4"
    ffmpeg -v error -y -threads 2 -f lavfi -i "color=0x1b1614:s=${w}x${h}:r=30" -framerate 30 -i $F -filter_complex "[0][1]overlay=shortest=1" -c:v libx264 -crf 20 -pix_fmt yuv420p "$O-preview.mp4"
  }
}
Get-ChildItem $out | Select-Object Name, Length
