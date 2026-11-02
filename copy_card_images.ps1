Add-Type -AssemblyName System.Drawing

$uploadDir = 'C:\Users\Margaret Maullon\.gemini\antigravity\brain\b0acdb2f-4847-40da-bfe1-e9d298ebb1e3\.user_uploaded'
$destDir   = 'C:\Users\Margaret Maullon\Documents\TalkCare\src\main\resources\static\assets'

$images = @(
    @{ src = 'media_1793342646276_d0dec88d.jpg'; dest = 'card-justice.png' },   # gavel
    @{ src = 'media_1793342652952_8b491540.jpg'; dest = 'card-individual.png' }, # girl
    @{ src = 'media_1793342658689_05ae33e8.jpg'; dest = 'card-peers.png' }       # two friends
)

foreach ($img in $images) {
    $srcPath  = Join-Path $uploadDir $img.src
    $destPath = Join-Path $destDir   $img.dest

    $bmp = [System.Drawing.Bitmap]::FromFile($srcPath)
    $out = New-Object System.Drawing.Bitmap($bmp.Width, $bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

    for ($x = 0; $x -lt $bmp.Width; $x++) {
        for ($y = 0; $y -lt $bmp.Height; $y++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.R -gt 240 -and $c.G -gt 240 -and $c.B -gt 240) {
                $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            } else {
                $out.SetPixel($x, $y, $c)
            }
        }
    }

    $out.Save($destPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $out.Dispose()
    Write-Output "Saved: $($img.dest)"
}

