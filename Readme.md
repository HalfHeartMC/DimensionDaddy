# DimensionDaddy
A Fabric mod for Minecraft 1.21.11 that lets operators enable or disable the End and Nether dimensions at will. Works on dedicated servers, singleplayer, and soft-hosted servers using mods like Essential. When a dimension is disabled, portal blocks are blocked and players attempting to enter receive an action bar message.
## Commands
Toggle commands require operator privileges. `/endstatus` and `/netherstatus` are usable by everyone.
| Command | Description |
|---|---|
| `/endstatus` | Shows the current End status |
| `/enableend` | Enables the End dimension |
| `/disableend` | Disables the End dimension |
| `/netherstatus` | Shows the current Nether status |
| `/enablenether` | Enables the Nether dimension |
| `/disablenether` | Disables the Nether dimension |
## Configuration
State is persisted in `config/DimensionDaddy/dimensiondaddy.properties`. The file is created automatically on first run with both dimensions enabled:
```properties
#DimensionDaddy Configuration
endEnabled=true
netherEnabled=true
```
You can edit this file directly while the server is stopped. Changes made via commands are written to it immediately.
## Requirements
- Minecraft 1.21.11
- Fabric Loader ≥ 0.18.2
- Fabric API
- Java 21
## Installation
1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `mods` folder.
3. Place the DimensionDaddy `.jar` into your `mods` folder.
4. Start your server.
## License
MIT