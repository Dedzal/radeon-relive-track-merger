
# Relive Track Merger

**Relive Track Merger** is a lightweight desktop application designed to simplify the process of integrating separated microphone audio tracks into Radeon Relive Replays. The merged files can then be directly imported into video editing software like DaVinci Resolve, with the microphone track already embedded.

## How It Works

1. **Select Input Folder**: Click the *"Select Folder"* button to choose the directory containing replays and corresponding microphone tracks. You can also select a folder containing game-specific subdirectories of replays for batch processing.
2. **Automatic File Listing**: The application scans the specified folder and its subdirectories for eligible replay files. Replays are identified as `.mp4` files with `"_replay_"` in their filenames. Already processed replays will be skipped (replays that end in `"_merged"`).
3. **Start Processing**: Press the *"Process"* button to begin merging the microphone tracks into the replay videos. The application provides real-time progress logs.
4. **View Output**: The merged files are saved in an output directory while maintaining the same folder structure as the input folder.

**Note**: A corresponding microphone track is identified as a file located in the same directory as the replay, sharing 
the same file name but with the `.m4a` extension. If no matching microphone track is found, the replay will be copied 
unchanged to the output directory.

### Prerequisites

- **FFmpeg**:  
  FFmpeg is required to merge the tracks. The application checks for FFmpeg during startup, and if it's missing, offers options to automatically install it via [Windows Package Manager (winget)](https://learn.microsoft.com/en-us/windows/package-manager/winget).  
  Alternatively, you can manually install FFmpeg from its [official website](https://ffmpeg.org/download.html).  
  **Note**: The application will not function without FFmpeg installed.

# Disclaimer

This application was created as a personal project and has been tested only my Windows 11 machine.
It might have bugs and may not work as expected if used in unintended ways. Use it at your own risk.
Contributions and improvements are always welcome!