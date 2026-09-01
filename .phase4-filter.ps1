$inputYaml = [Console]::In.ReadToEnd()
$documents = [regex]::Split($inputYaml, '(?m)^---\s*$')
$kept = foreach ($document in $documents) {
    if ($document -notmatch '(?m)^# Source: dws/charts/dapr/') {
        $document.Trim()
    }
}
[Console]::Out.Write(($kept -join "`n---`n"))
