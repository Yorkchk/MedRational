param (
    [Parameter(Mandatory=$true)]
    [string]$DumpPath
)

# 1. Setup and resolve paths
$AbsoluteDumpPath = Resolve-Path $DumpPath -ErrorAction SilentlyContinue
if (-not $AbsoluteDumpPath) {
    Write-Error "Error: File not found at '$DumpPath'"
    exit 1
}
$FileFolder = Split-Path $AbsoluteDumpPath
$FileName = Split-Path $AbsoluteDumpPath -Leaf

# 2. Cleanup any leftover containers from a previous crash
docker rm -f pg-terminal-db 2>$null

Write-Host "Starting background Postgres instance..."
# Start Postgres container in the background
docker run -d `
    --name pg-terminal-db `
    -e POSTGRES_DB=mydb `
    -e POSTGRES_PASSWORD=secret `
    -v "${FileFolder}:/data" `
    postgres:alpine > $null

Write-Host "Waiting for engine initialization..."
Start-Sleep -Seconds 5

Write-Host "Restoring $FileName..."
# Restore the PGDMP file into the database
docker exec pg-terminal-db pg_restore -U postgres -d mydb "/data/$FileName" 2>$null

Write-Host "--------------------------------------------------"
Write-Host "Launching Interactive SQL Console."
Write-Host "Type your queries here. Type \q and press Enter to exit."
Write-Host "--------------------------------------------------"

# 3. Connect interactively.
# -c runs a command, but if we want it to stay open we pass the command via -P or pre-populate hist.
# The cleanest way to run a query AND stay open in psql is using the --command evaluation combined with a shell choice:
docker exec -it pg-terminal-db psql -U postgres -d mydb -c "SELECT * FROM users;"

Write-Host "--------------------------------------------------"
Write-Host "Initial query complete. Dropping into persistent shell..."
Write-Host "--------------------------------------------------"

# Drop into the live interactive shell
docker exec -it pg-terminal-db psql -U postgres -d mydb

# 4. Wipe everything the second the user exits the terminal console
Write-Host "`nExited console. Cleaning up containers..."
docker rm -f pg-terminal-db > $null
Write-Host "System clean!"



# Run the script like this: .\scripts\View-Dump.ps1 "C:\Users\youss\Downloads\prod_medrational-prod-2026-11-07T1639Z.dump"