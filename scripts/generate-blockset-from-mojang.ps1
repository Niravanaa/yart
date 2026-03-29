param(
    [string]$Version = "latest",
    [string]$OutputPath = "minecraft-test/plugins/YetAnotherRayTracer/blockset.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing

function Get-AverageRgbFromPngEntry {
    param(
        [System.IO.Compression.ZipArchiveEntry]$Entry
    )

    $stream = $Entry.Open()
    try {
        $bitmap = New-Object System.Drawing.Bitmap($stream)
        try {
            $sumR = 0.0
            $sumG = 0.0
            $sumB = 0.0
            $count = 0

            for ($y = 0; $y -lt $bitmap.Height; $y++) {
                for ($x = 0; $x -lt $bitmap.Width; $x++) {
                    $pixel = $bitmap.GetPixel($x, $y)
                    if ($pixel.A -lt 16) {
                        continue
                    }

                    $sumR += $pixel.R
                    $sumG += $pixel.G
                    $sumB += $pixel.B
                    $count++
                }
            }

            if ($count -eq 0) {
                return $null
            }

            return @(
                [Math]::Round(($sumR / $count) / 255.0, 6),
                [Math]::Round(($sumG / $count) / 255.0, 6),
                [Math]::Round(($sumB / $count) / 255.0, 6)
            )
        }
        finally {
            $bitmap.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}

function Convert-BlockIdToMaterial {
    param([string]$BlockId)

    return $BlockId.ToUpperInvariant().Replace("-", "_").Replace("/", "_")
}

function Resolve-Version {
    param([string]$RequestedVersion)

    $manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    $manifest = Invoke-RestMethod -Uri $manifestUrl -Method Get

    if ($RequestedVersion -eq "latest") {
        $RequestedVersion = $manifest.latest.release
    }

    $selected = $manifest.versions | Where-Object { $_.id -eq $RequestedVersion } | Select-Object -First 1
    if (-not $selected) {
        throw "Version '$RequestedVersion' not found in Mojang manifest."
    }

    return $selected
}

$excludedMaterialPattern = '(_SAPLING|_TRAPDOOR|_DOOR)$'

Write-Host "Resolving Minecraft version..." -ForegroundColor Cyan
$selectedVersion = Resolve-Version -RequestedVersion $Version
$resolvedVersionId = $selectedVersion.id
Write-Host "Using version: $resolvedVersionId" -ForegroundColor Green

Write-Host "Fetching version metadata..." -ForegroundColor Cyan
$versionInfo = Invoke-RestMethod -Uri $selectedVersion.url -Method Get
if (-not $versionInfo.downloads.client.url) {
    throw "No client JAR download URL in version metadata for $resolvedVersionId."
}

$clientJarUrl = $versionInfo.downloads.client.url
$tempJar = Join-Path $env:TEMP ("mc-client-{0}.jar" -f $resolvedVersionId)

Write-Host "Downloading client JAR..." -ForegroundColor Cyan
Invoke-WebRequest -Uri $clientJarUrl -OutFile $tempJar

Write-Host "Reading textures and blockstates from JAR..." -ForegroundColor Cyan
$zip = [System.IO.Compression.ZipFile]::OpenRead($tempJar)

try {
    $animatedTextureSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.StartsWith("assets/minecraft/textures/block/", [System.StringComparison]::OrdinalIgnoreCase) -and
            $entry.FullName.EndsWith(".png.mcmeta", [System.StringComparison]::OrdinalIgnoreCase)) {
            $animatedTextureSet.Add($entry.FullName.Substring(0, $entry.FullName.Length - ".mcmeta".Length)) | Out-Null
        }
    }

    $textureRgbMap = @{}

    foreach ($entry in $zip.Entries) {
        if (-not $entry.FullName.StartsWith("assets/minecraft/textures/block/", [System.StringComparison]::OrdinalIgnoreCase)) {
            continue
        }
        if (-not $entry.FullName.EndsWith(".png", [System.StringComparison]::OrdinalIgnoreCase)) {
            continue
        }
        if ($animatedTextureSet.Contains($entry.FullName)) {
            continue
        }

        $relative = $entry.FullName.Substring("assets/minecraft/textures/block/".Length)
        $textureKey = $relative.Substring(0, $relative.Length - 4) # trim .png

        $rgb = Get-AverageRgbFromPngEntry -Entry $entry
        if ($null -eq $rgb) {
            continue
        }

        $textureRgbMap[$textureKey] = $rgb
    }

    if ($textureRgbMap.Count -eq 0) {
        throw "No block textures were extracted from client JAR."
    }

    $candidateSuffixes = @(
        "",
        "_top",
        "_side",
        "_front",
        "_back",
        "_end",
        "_bottom",
        "_still",
        "_base",
        "_0"
    )

    $blockEntries = New-Object System.Collections.Generic.List[object]
    $seenMaterials = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

    foreach ($entry in $zip.Entries) {
        if (-not $entry.FullName.StartsWith("assets/minecraft/blockstates/", [System.StringComparison]::OrdinalIgnoreCase)) {
            continue
        }
        if (-not $entry.FullName.EndsWith(".json", [System.StringComparison]::OrdinalIgnoreCase)) {
            continue
        }

        $filename = [System.IO.Path]::GetFileNameWithoutExtension($entry.FullName)
        if ([string]::IsNullOrWhiteSpace($filename)) {
            continue
        }

        $material = Convert-BlockIdToMaterial -BlockId $filename
        if ($material -match $excludedMaterialPattern) {
            continue
        }
        if ($seenMaterials.Contains($material)) {
            continue
        }

        $rgb = $null
        foreach ($suffix in $candidateSuffixes) {
            $candidate = "$filename$suffix"
            if ($textureRgbMap.ContainsKey($candidate)) {
                $rgb = $textureRgbMap[$candidate]
                break
            }
        }

        if ($null -eq $rgb) {
            continue
        }

        $blockEntries.Add([ordered]@{
                material = $material
                rgb      = @([double]$rgb[0], [double]$rgb[1], [double]$rgb[2])
            })
        $seenMaterials.Add($material) | Out-Null
    }

    if ($blockEntries.Count -eq 0) {
        throw "No blockstate-to-texture mappings were generated."
    }

    $sortedBlocks = $blockEntries | Sort-Object material
    $payload = [ordered]@{
        name       = "mojang-$resolvedVersionId-generated"
        colorSpace = "srgb"
        blocks     = $sortedBlocks
    }

    $resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputPath))
    $outputDir = Split-Path -Parent $resolvedOutput
    if (-not (Test-Path -Path $outputDir)) {
        New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    }

    ($payload | ConvertTo-Json -Depth 8) | Set-Content -Path $resolvedOutput -Encoding UTF8

    Write-Host "Generated blockset: $resolvedOutput" -ForegroundColor Green
    Write-Host "Version: $resolvedVersionId" -ForegroundColor Green
    Write-Host "Mapped blocks: $($sortedBlocks.Count)" -ForegroundColor Green
}
finally {
    $zip.Dispose()
    if (Test-Path -Path $tempJar) {
        Remove-Item -Path $tempJar -Force
    }
}
