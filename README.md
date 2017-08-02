# JieCaoVideoPlayer
## 在JieCaoVideoPlayer (播放内核使用的是ijkplayer)基础上进行完善


## 相关网址如下
https://github.com/lipangit/JieCaoVideoPlayer


https://github.com/Bilibili/ijkplayer


## Android Studio添加依赖方式如下
Add it in your root build.gradle at the end of repositories:

	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}Copy
Step 2. Add the dependency

	dependencies {
	        compile 'com.github.flztsj:JieCaoVideoPlayer:4.8.6'
	}
