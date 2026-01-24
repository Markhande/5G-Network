# 5G Network

🎯 Why This App?
Most smartphones automatically switch between 5G and 4G networks based on signal strength, but they don't give users the option to strictly stay on 5G mode. This becomes frustrating when:

📞 Your video calls get interrupted due to unexpected network switches
📥 Downloads slow down mid-transfer
🎮 Online gaming sessions lag due to network mode changes
⚡ You need consistent high-speed 5G connectivity for work

I faced this problem myself - my smartphone had no option to lock onto 5G only. So I built this app to solve it.
✨ Features

🔄 One-Tap Network Switching - Switch between 5G-only and LTE-only modes instantly
🏠 Home Screen Widget - Quick access without opening the app
📊 Real-Time Network Status - See your current network mode at a glance
🔧 Phone Info Access - Direct access to Android's hidden network testing menu
🔒 Multiple Compatibility Methods - Works across different Android device manufacturers
🔋 Lightweight - Minimal battery and resource usage

🚀 How It Works
The app uses multiple approaches to ensure compatibility across different Android devices:

Broadcast Intent Method - For devices that support custom network mode intents
Content Provider Method - Direct access to telephony database
TelephonyManager with Subscription ID - Modern API approach
Reflection API - Accessing hidden system methods
Root Access (Optional) - For rooted devices with full system access

The app automatically tries these methods in order, falling back to the next if one doesn't work on your device.
📋 Requirements


📱 How to Use
Using the Widget

Long-press on your home screen
Select "Widgets"
Find "Network Switcher" and drag it to your home screen
Tap "LTE" button to switch to 4G-only mode
Tap "5G" button to switch to 5G-only mode

Using the App

Open the Network Switcher app
Grant necessary permissions
Use the buttons to:

Settings - Open network operator settings
Testing - Access phone info/testing menu
LTE Only - Switch to 4G-only and open testing menu
NR Only - Switch to 5G-only and open testing menu



⚙️ Technical Details
Architecture

Language: Kotlin
Min SDK: 24 (Android 7.0)
Target SDK: Latest
Architecture Pattern: Activity-based with Widget Provider

Key Components

MainActivity.kt - Main app interface
NetworkWidget.kt - Home screen widget implementation
NetworkStateReceiver.kt - Broadcast receiver for network changes

Network Mode Values

11 - LTE only
20 - NR (5G) only

⚠️ Known Limitations

Device-Specific Behavior: Some manufacturers lock down network mode changes
Permission Restrictions: MODIFY_PHONE_STATE requires system-level access on most devices
Root Access: May be required on some devices for full functionality
Testing Menu Access: Phone info menu availability varies by manufacturer

🤝 Contributing
Contributions are welcome! Here's how you can help:

Fork the repository
Create a feature branch (git checkout -b feature/amazing-feature)
Commit your changes (git commit -m 'Add amazing feature')
Push to the branch (git push origin feature/amazing-feature)
Open a Pull Request

Android 7.0 (API 24) or higher
Permissions:

READ_PHONE_STATE - To detect current network mode
ACCESS_NETWORK_STATE - To monitor network connectivity
MODIFY_PHONE_STATE - To change network settings (system-level)
WRITE_SETTINGS - To modify system settings

Note: MODIFY_PHONE_STATE is a signature-level permission. The app attempts to use it but may require additional device-specific workarounds or root access on some phones.
