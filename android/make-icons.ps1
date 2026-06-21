# Generates the app launcher icons (square + round) and the Android TV banner
# into res/, using System.Drawing. Re-run after tweaking the design.
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$here = Split-Path -Parent $MyInvocation.MyCommand.Definition
$res  = Join-Path $here "res"

# Brand colours.
$blueTop = [System.Drawing.Color]::FromArgb(255, 91, 141, 239)   # #5B8DEF
$blueBot = [System.Drawing.Color]::FromArgb(255, 45, 108, 223)   # #2D6CDF
$white   = [System.Drawing.Color]::White

function New-RoundedRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

# Draws the CamOverlay mark (a screen with a picture-in-picture window) onto $g
# inside a square of side $s. $round selects a circular vs rounded-square plate.
function Draw-Mark([System.Drawing.Graphics]$g, [int]$s, [bool]$round) {
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    $rect = New-Object System.Drawing.RectangleF(0, 0, $s, $s)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $blueTop, $blueBot, 90)

    # Plate
    if ($round) {
        $g.FillEllipse($brush, 0, 0, $s, $s)
    } else {
        $plate = New-RoundedRect 0 0 $s $s ($s * 0.235)
        $g.FillPath($brush, $plate)
    }

    # Screen outline
    $m = $s * 0.24
    $sw = $s - 2 * $m
    $sh = $sw * 0.66
    $sx = $m
    $sy = ($s - $sh) / 2
    $penW = [Math]::Max(2, $s * 0.05)
    $pen = New-Object System.Drawing.Pen($white, $penW)
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $screen = New-RoundedRect $sx $sy $sw $sh ($s * 0.055)
    $g.DrawPath($pen, $screen)

    # Picture-in-picture window (filled), bottom-right inside the screen
    $pw = $sw * 0.42
    $ph = $pw * 0.6
    $inset = $s * 0.045
    $px = $sx + $sw - $pw - $inset
    $py = $sy + $sh - $ph - $inset
    $wb = New-Object System.Drawing.SolidBrush($white)
    $pip = New-RoundedRect $px $py $pw $ph ($s * 0.03)
    $g.FillPath($wb, $pip)

    $pen.Dispose(); $brush.Dispose(); $wb.Dispose()
}

function Write-Icon([string]$folder, [string]$name, [int]$s, [bool]$round) {
    $dir = Join-Path $res $folder
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $bmp = New-Object System.Drawing.Bitmap($s, $s)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    Draw-Mark $g $s $round
    $bmp.Save((Join-Path $dir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    Write-Host "  $folder/$name.png  ($s x $s)"
}

# Launcher icons (square + round) at every density.
$dens = @{ "mipmap-mdpi" = 48; "mipmap-hdpi" = 72; "mipmap-xhdpi" = 96; "mipmap-xxhdpi" = 144; "mipmap-xxxhdpi" = 192 }
foreach ($d in $dens.GetEnumerator()) {
    Write-Icon $d.Key "ic_launcher" $d.Value $false
    Write-Icon $d.Key "ic_launcher_round" $d.Value $true
}

# Android TV banner: 320x180 plate with the mark on the left and the name on the right.
$bdir = Join-Path $res "drawable-xhdpi"
New-Item -ItemType Directory -Force -Path $bdir | Out-Null
$bw = 320; $bh = 180
$bmp = New-Object System.Drawing.Bitmap($bw, $bh)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
$brect = New-Object System.Drawing.RectangleF(0, 0, $bw, $bh)
$bgrad = New-Object System.Drawing.Drawing2D.LinearGradientBrush($brect, ([System.Drawing.Color]::FromArgb(255,23,27,34)), ([System.Drawing.Color]::FromArgb(255,14,17,22)), 90)
$g.FillRectangle($bgrad, $brect)
# mark on the left
$marksz = 108
$mbmp = New-Object System.Drawing.Bitmap($marksz, $marksz)
$mg = [System.Drawing.Graphics]::FromImage($mbmp)
Draw-Mark $mg $marksz $false
$g.DrawImage($mbmp, 22, ($bh - $marksz) / 2, $marksz, $marksz)
$font = New-Object System.Drawing.Font("Segoe UI", 20, [System.Drawing.FontStyle]::Bold)
$tb = New-Object System.Drawing.SolidBrush($white)
$fmt = New-Object System.Drawing.StringFormat
$fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
$fmt.FormatFlags = [System.Drawing.StringFormatFlags]::NoWrap
$g.DrawString("CamOverlay", $font, $tb, (New-Object System.Drawing.RectangleF(142, 0, 178, $bh)), $fmt)
$bmp.Save((Join-Path $bdir "banner.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$mg.Dispose(); $mbmp.Dispose(); $g.Dispose(); $bmp.Dispose()
Write-Host "  drawable-xhdpi/banner.png  (320 x 180)"

Write-Host "DONE -> $res"
