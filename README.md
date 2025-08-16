# Densha no Omocha

An experimental version of an upcoming train mod. Adds toy trains to Minecraft!

## About
電車のおもちゃ Densha no Omocha was created for ModFest: ToyBox. This is an early experimental project, so expect bugs and performance issues for now.

## Features
- Tracks that connect with nodes
- Custom tracks with OBJ models
- Different track types like platform and depot tracks
- Automatic train driving
- Platform detection on single paths (no route support or complex pathfinding yet)

All trains and tracks use OBJ models only. By default, Densha no Omocha includes a default toy track and toy train. Currently there are no survival recipes.
Other features include door animations (no boarding system yet), trains with interior lighting that turn on while the train is on a path, experimental physics-based movement that's not great, custom bogie support, and custom train configurations.
Trains are positioned by their bogies, and tracks can be arranged (hopefully !!) however you want.

## How to use
Use the Crowbar and right click on a track segment to manage it. Click Save or ESC to save changes, Cancel to abort. As soon as you have a valid configuration, you'll see the train start moving on its path. If you update platforms or other things, click "Refresh Path".
To place tracks, use track nodes. Right click the first node with the Track Item, then right click the second node. Break a node to break the track. Make sure all nodes are facing forward in the direction you place them.

> **NOTE** - You may notice some text fields in the Track Configuration screen. For slopes, you can change the curvature. For platforms, you can change the dwell time (15 seconds by default). If the platform track is also a slope, you'll see both.

## And one more thing...
Boarding should be a thing in a day or a few hours from when this README is created. As for manual driving and other things, Densha no Omocha will become a train mod separate from this project, that will add many things a train mod should have. In the meantime, Densha no Omocha will probably get route support and more tweaks that are required for ModFest: ToyBox.