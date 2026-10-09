# PrimeModel

Modern Bedrock model engine for Minecraft Java Edition.

## Features

- Custom 3D model rendering
- Animation support
- Multi-version NMS support (1.21 R6/R7, 26 R1/R2/R3)
- Bukkit / Spigot / Paper platform support
- Folia support
- Citizens / SkinsRestorer / Nexo compatibility

## Requirements

- Java 21+
- Minecraft 1.21.4+ (Paper/Spigot)

## Installation

1. Download the latest release from Releases page
2. Place the .jar file in your server's plugins/ folder
3. Restart the server
4. Configure plugins/PrimeModel/config.yml

## Building

Run: gradlew build

The output .jar will be in platform/paper/build/libs/ or platform/spigot/build/libs/.

## Commands

- /primemodel reload - Reload the plugin
- /primemodel spawn - Spawn a model
- /primemodel disguise - Disguise as model
- /primemodel undisguise - Remove disguise
- /primemodel test - Test model
- /primemodel play - Play animation
- /primemodel version - Show version
- /primemodel hide - Hide model
- /primemodel show - Show model

## License

MIT License - see LICENSE file for details.

## Author

zRioxS (https://github.com/zRioxS)
