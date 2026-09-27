# Renders one deck module as a transparent clip in the given aspects.
# powershell -File promo/clips/render-clip.ps1 -Module barometer -Seconds 10 -Name Barometer [-Aspects "16:9","3:2","3:4"] [-Opts '{}']
param(
  [Parameter(Mandatory)] [string]$Module,
  [Parameter(Mandatory)] [double]$Seconds,
  [Parameter(Mandatory)] [string]$Name,
  [string[]]$Aspects = @("16:9", "3:2", "3:4"),
  [string]$Opts = "{}"
)
$deck = Join-Path $PSScriptRoot "..\deck"
$out = Join-Path $PSScriptRoot "out"
$cap = "C:\Users\rapha\AppData\Local\Temp\claude\cap\$Name"
New-Item -ItemType Directory -Force $out | Out-Null
$sizes = @{ "16:9" = @(1920, 1080); "3:2" = @(2160, 1440); "3:4" = @(1620, 2160) }

foreach ($a in $Aspects) {
  $w, $h = $sizes[$a]
  $tag = $a.Replace(":", "x")
  $frames = Join-Path $cap $tag
  $json = $Opts.TrimEnd("}").TrimEnd() + (if ($Opts.Trim() -eq "{}") { "" } else { "," }) + "`"aspect`":`"$a`"}"
  $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($json))
  Write-Host "== $Name $a  $json"
  Push-Location $deck
  node capture.js $Module $Seconds $frames --fps=30 --warm=0 --w=$w --h=$h --b64=$b64 --pre="window.__handle.play()" 2>&1 | Select-String "done|pageerror|error"
  Pop-Location
  $F = Join-Path $frames "f_%05d.png"
  $O = Join-Path $out "$Name-$tag"
  ffmpeg -v error -y -threads 2 -framerate 30 -i $F -c:v libvpx-vp9 -pix_fmt yuva420p -auto-alt-ref 0 -crf 20 -b:v 0 "$O-alpha.webm"
  ffmpeg -v error -y -threads 2 -f lavfi -i "color=0x00FF00:s=${w}x${h}:r=30" -framerate 30 -i $F -filter_complex "[0][1]overlay=shortest=1" -c:v libx264 -crf 16 -pix_fmt yuv420p "$O-greenscreen.mp4"
  ffmpeg -v error -y -threads 2 -f lavfi -i "color=0x1b1614:s=${w}x${h}:r=30" -framerate 30 -i $F -filter_complex "[0][1]overlay=shortest=1" -c:v libx264 -crf 20 -pix_fmt yuv420p "$O-preview.mp4"
}
Get-ChildItem $out -Filter "$Name-*" | Select-Object Name, Length
