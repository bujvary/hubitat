/*
 *  Vehcile Remote Starter App v1.0.0	(Apps Code)
 *
 *  Changelog:
 *
 *    1.1 (10/26/2021)
 *      - Added check for relay switch on in childStart()
 *
 *    1.0 (10/20/2021)
 *      - Initial Release
 *
 *
 *  Copyright 2021 Brian Ujvary
 *
 *  This app is based on the work of Kevin LaFramboise - Zooz Garage Door Opener for Hubitat
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
import groovy.transform.Field

@Field static Map delayOptions = [0:"Disabled", 500:"500 Milliseconds  [DEFAULT]", 750:"750 Milliseconds", 1000:"1000 Milliseconds", 1250:"1250 Milliseconds", 1500:"1500 Milliseconds", 2000:"2000 Milliseconds"]
@Field static Map toggleOptions = [1:"1 Toggle", 2:"2 Toggles", 3:"3 Toggles  [DEFAULT]", 4:"4 Toggles", 5:"5 Toggles", 6:"6 Toggles"]
@Field static Map durationOptions = [5:"5 Seconds", 6:"6 Seconds", 7:"7 Seconds", 8:"8 Seconds", 9:"9 Seconds", 10:"10 Seconds [DEFAULT]", 11:"11 Seconds", 12:"12 Seconds", 13:"13 Seconds", 14:"14 Seconds", 15:"15 Seconds", 16:"16 Seconds", 17:"17 Seconds", 18:"18 Seconds", 19:"19 Seconds", 20:"20 Seconds", 21:"21 Seconds", 22:"22 Seconds", 23:"23 Seconds", 24:"24 Seconds", 25:"25 Seconds", 26:"26 Seconds", 27:"27 Seconds", 28:"28 Seconds", 29:"29 Seconds", 30:"30 Seconds"]
@Field static Map daysOptions = [0:"Sunday", 1:"Monday", 2:"Tuesday", 3:"Wednesday", 4:"Thursday", 5:"Friday",6:"Saturday"]

definition(
	name: "Vehicle Remote Starter App",
	namespace: "bujvary",
	author: "Brian Ujvary",
	description: "DO NOT INSTALL, use the Vehcile Remote Starter instead.",
	parent: "bujvary:Vehicle Remote Starter",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/apps/vehicle-remote-starter-app.groovy"
)

preferences {
	page(name:"pageMain")
	page(name:"pageDelayTimers", nextPage: "pageMain")
	page(name:"pageDurations", nextPage: "pageMain")
	page(name:"pageNotifications", nextPage: "pageMain")
	page(name:"pageRemove")
    page(name:"pageSchedule", nextPage: "pageMain")
	page(name:"pageToggles", nextPage: "pageMain")
}

def pageMain() {
	dynamicPage(name: "pageMain", title: "", install: true, uninstall: false) {

		section("<big><b>Vehicle Name</b></big>") {
			paragraph "The Vehicle Name will be displayed in the Vehicle list when you open this app."

			paragraph "The app will create a new Device with the same name that you can use to start, stop and lock your vehicle from Hubitat."

			label title: "<b>Enter Vehicle Name:</b>", required: true

			paragraph ""
		}

		section("<big><b>Relay Switch</b></big>") {
			paragraph "The Relay Switch will be used to start, stop and lock the vehicle."

			input "relaySwitch", "capability.switch",
				title: "<b>Select Relay Switch:</b>",
				required: true

			paragraph ""
		}

		section("<big><b>Vehicle State Sensor</b></big>") {
			paragraph "The Contact Sensor will be used to determine the running state of the vehicle."

			input "vehicleContactSensor", "capability.contactSensor",
				title: "<b>Select Vehicle Sensor:</b>",
				required: true

			paragraph ""
		}

		section("<big><b>Configuration Options</b></big>") {
			href "pageDelayTimers", title: "Relay Delay Timers", description: "Click here for Options"
		}

		section("") {
			href "pageToggles", title: "Start/Stop/Lock Toggles", description: "Click here for Options"
		}

		section("") {
			href "pageDurations", title: "Start/Stop Durations", description: "Click here for Options"
		}

		section("") {
			href "pageNotifications", title: "Notifications", description: "Click here for Options"
		}

		section("<big><b>Logging</b></big>") {
			paragraph "<i>Automatically disables after 15 minutes</i>"

			input "debugLogging", "bool",
				title: "<b>Enable debug logging?</b>",
				defaultValue: true,
				required: false
		}

		if (state.installed) {
			section() {
				href "pageRemove", title: "Remove Vehicle", description: ""
			}
		}
	}
}

def pageDelayTimers() {
	dynamicPage(name: "pageDelayTimers", title: "", install: false, uninstall: false) {
		section("<big><b>Relay Switch Auto-Off Timer</b></big>") {
			paragraph "The Auto-Off Timer will turn the relay off after the selected number of milliseconds making it a momentary switch."

			paragraph "Note: Leave this setting disabled if you already set an auto-off parameter on the relay device."

			input "autoOffDelay", "enum",
				title: "<b>Select Auto-Off Timer:</b>",
				required: false,
				defaultValue: autoOffDelaySetting,
				options: delayOptions

			paragraph ""
		}

		section("<big><b>Relay Switch Toggle Delay Timer</b></big>") {
			paragraph "The Toggle Delay Timer will delay successive relay ON commands by the selected number of milliseconds."

			input "toggleDelay", "enum",
				title: "<b>Select Toggle Delay Timer:</b>",
				required: false,
				defaultValue: toggleDelaySetting,
				options: delayOptions

			paragraph ""
		}
	}
}

def pageDurations() {
	dynamicPage(name: "pageDurations", title: "", install: false, uninstall: false) {
		section("<big><b>Vehicle Start Duration</b></big>") {
			paragraph "The Vehicle Start Duration should be set to a value greater than or equal to the amount of time it takes for the vehicle to start."

			input "operatingDuration", "enum",
				title: "<b>Select Vehicle Start Duration:</b>",
				required: false,
				defaultValue: startDurationSetting,
				options: durationOptions

			paragraph ""
		}

		section("<big><b>Vehicle Stop Duration</b></big>") {
			paragraph "The Vehicle Stop Duration should be set to a value greater than or equal to the amount of time it takes for the vehicle to stop."

			input "operatingDuration", "enum",
				title: "<b>Select Vehicle Stop Duration:</b>",
				required: false,
				defaultValue: stopDurationSetting,
				options: durationOptions

			paragraph ""
		}
	}
}

def pageNotifications() {
	dynamicPage(name: "pageNotifications", title: "", install: false, uninstall: false) {
		section("<big><b>Notifications</b></big>") {
			paragraph "Send push notification when vehicle fails to start or stop."

			input "failedStartDevices", "capability.notification",
				title: "<b>Send notification to these devices(s) when vehicle fails to Start:</b>",
				multiple: true,
				required: false

			input "failedStopDevices", "capability.notification",
				title: "<b>Send notification to these devices(s) when vehicle fails to Stop:</b>",
				multiple: true,
				required: false

			paragraph ""

			paragraph "Send audio notification when vehicle fails to start or stop."

			input "failedStartTTSDevices", "capability.speechSynthesis",
				title: "<b>Send audio notification to these devices(s) when vehicle fails to Start:</b>",
				multiple: true,
				required: false

			input "failedStopTTSDevices", "capability.speechSynthesis",
				title: "<b>Send audio notification to these devices(s) when vehicle fails to Stop:</b>",
				multiple: true,
				required: false

			paragraph ""
		}
    }
}

def pageToggles() {
	dynamicPage(name: "pageToggles", title: "", install: false, uninstall: false) {
		section("<big><b>Start Vehicle Toggle</b></big>") {
			paragraph "The number of times to toggle the relay to start the vehicle."

			input "numStartToggles", "enum",
				title: "<b>Select Number of Toggles:</b>",
				required: false,
				defaultValue: startToggleSetting,
				options: toggleOptions

			paragraph ""
		}

		section("<big><b>Stop Vehicle Toggle</b></big>") {
			paragraph "The number of times to toggle the relay to stop the vehicle."

			input "numStopToggles", "enum",
				title: "<b>Select Number of Toggles:</b>",
				required: false,
				defaultValue: stopToggleSetting,
				options: toggleOptions

			paragraph ""
		}

		section("<big><b>Lock Vehicle Toggle</b></big>") {
			paragraph "The number of times to toggle the relay to lock the vehicle."

			input "numLockToggles", "enum",
				title: "<b>Select Number of Toggles:</b>",
				required: false,
				defaultValue: lockToggleSetting,
				options: toggleOptions

			paragraph ""
		}
    }
}

def pageRemove() {
	dynamicPage(name: "pageRemove", title: "", install: false, uninstall: true) {
		section() {
			paragraph "<b>WARNING:</b> You are about to remove this vehicle device.", required: true, state: null
		}
	}
}

def uninstalled() {
	try {
		childDevices?.each {
			deleteChildDevice(it.deviceNetworkId)
		}
	}
	catch (ex) {

	}
}

def installed() {
	log.warn "installed()..."

	state.installed = true

	initialize()
}

def updated() {
	log.warn "updated()..."

	unsubscribe()
	unschedule()
	initialize()
}

void initialize() {
	def vehicle = childVehicle
	if (!vehicle) {
		runIn(3, createChildVehicle)
	}
    
	subscribe(settings?.relaySwitch, "switch.on", relaySwitchOnEventHandler)
    subscribe(settings?.relaySwitch, "switch.off", relaySwitchOffEventHandler)
	subscribe(settings?.vehicleContactSensor, "contact", vehicleContactEventHandler)

	if (settings?.debugLogging) runIn(900, logsOff)
}

def createChildVehicle() {
	def child
	def name = "${app.label}"
	logDebug "Creating ${name}"

	try {
		child = addChildDevice(
			"bujvary",
			"Vehicle",
			"${app.id}-vehicle",
			null,
			[
				name: "Vehicle",
				label: "${name}",
				completedSetup: true,
				isComponent: true
			]
		)
	}
	catch (ex) {
		log.error "Unable to create the Vehicle.  You must install the Vehicle Driver in order to use this App."
	}
	return child
}

void childStart(childDNI) {
    logDebug "${childVehicle?.displayName} - childStart()"

	String vehicleContactStatus = settings?.vehicleContactSensor?.currentValue("contact")
    
	if (vehicleContactStatus == "open") {  
		if (settings?.relaySwitch?.currentValue("switch") == "on") {
			// The switch is still on for some reason which will prevent the relay from triggering the vehicle next time so turn it off.
			logDebug "Turning off Relay Switch (backup)..."
			settings?.relaySwitch?.off()
		}
        
		state.vehicleStatus = "starting"
		state.relayToggleCount = numStartTogglesSetting
        
		logDebug "state.relayToggleCount: ${state?.relayToggleCount}"
        
		turnOnRelaySwitch()
	}
	else {
        logDebug "${childVehicle?.displayName} is already running"
	}
}

void childStop(childDNI) {
	logDebug "${childVehicle?.displayName} - childStop()"

	String vehicleContactStatus = settings?.vehicleContactSensor?.currentValue("contact")

	if (vehicleContactStatus == "closed") {
		state.vehicleStatus = "stopping"
		state.relayToggleCount = numStopTogglesSetting
        
		logDebug "state.relayToggleCount: ${state?.relayToggleCount}"
        
		turnOnRelaySwitch()
	}
	else {
		logDebug "${childVehicle?.displayName} is already stopped"
	}
}

void childLock(childDNI) {
	logDebug "${childVehicle?.displayName} - childLock()"

	state.relayToggleCount = numLockTogglesSetting
    logDebug "state.relayToggleCount: ${state?.relayToggleCount}"
	turnOnRelaySwitch()
}

void turnOnRelaySwitch() {
	logDebug "${childVehicle?.displayName} - Turning on Relay Switch..."

	settings?.relaySwitch?.on()
    
	if (state?.vehicleStatus == "starting") {
		runIn(startDurationSetting, checkVehicleStatus)
	}
    
	if (state?.vehicleStatus == "stopping") {
		runIn(stopDurationSetting, checkVehicleStatus)
	}
}

void relaySwitchOnEventHandler(evt) {
	logDebug "${childVehicle?.displayName} - Relay Switch Turned ${evt.value}"
    
	if (autoOffDelaySetting) {
        runInMillis(autoOffDelaySetting, turnOffRelaySwitch)
	}
}

void turnOffRelaySwitch() {
	logDebug "${childVehicle?.displayName} - Turning off Relay Switch..."

	settings?.relaySwitch?.off()
}

void relaySwitchOffEventHandler(evt) {
	logDebug "${childVehicle?.displayName} - Relay Switch Turned ${evt.value}"

	state.relayToggleCount--

	if (state.relayToggleCount > 0) {
		if (toggleDelaySetting) {
			runInMillis(toggleDelaySetting, turnOnRelaySwitch)
		}
		else {
			turnOnRelaySwitch()
        }
	}
}

void vehicleContactEventHandler(evt) {
	logDebug "${settings?.vehicleContactSensor?.displayName} changed to ${evt.value}"

	def vehicle = childVehicle

	if (evt.value == 'closed') {
		vehicle?.parse([name: "switch", value: "on", displayed: true])
	}

	if (evt.value == 'open') {
		vehicle?.parse([name: "switch", value: "off", displayed: true])
	}
}

void checkVehicleStatus() {
	logDebug "${childVehicle?.displayName} - checkVehicleStatus()..."

	String vehicleContactStatus = settings?.vehicleContactSensor?.currentValue("contact")
    
	if (state?.vehicleStatus == "starting" && vehicleContactStatus != "closed") {
		sendFailedNotification("start")
	}
    
	if (state?.vehicleStatus == "stopping" && vehicleContactStatus != "open") {
		sendFailedNotification("stop")
	}
}

void sendFailedNotification(String failedStatus) {
	String msg = "${childVehicle?.displayName} failed to ${failedStatus}"
	
	def pushDevices = (failedStatus == "start") ? settings?.failedStartDevices : settings?.failedStopDevices	
	if (pushDevices) {
		logDebug "${childVehicle?.displayName} - Sending push notification: ${msg}"
		pushDevices*.deviceNotification(msg)
	}
    
	def ttsDevices = (failedStatus == "start") ? settings?.failedStartTTSDevices : settings?.failedStopTTSDevices
	if (ttsDevices) {
		logDebug "${childVehicle?.displayName} - Sending audio notification: ${msg}"
		ttsDevices.each{ device ->
			device.speak(msg)
		}
	}
}

def getChildVehicle() {
	return childDevices?.find { it.deviceNetworkId?.endsWith("-vehicle") }
}

Integer getAutoOffDelaySetting() {
	return safeToInt((settings ? settings["autoOffDelay"] : null), 500)
}

Integer getToggleDelaySetting() {
	return safeToInt((settings ? settings["toggleDelay"] : null), 500)
}

Integer getNumLockTogglesSetting() {
	return safeToInt((settings ? settings["numLockToggles"] : null), 2)
}

Integer getNumStartTogglesSetting() {
	return safeToInt((settings ? settings["numStartToggles"] : null), 3)
}

Integer getNumStopTogglesSetting() {
	return safeToInt((settings ? settings["numStopToggles"] : null), 3)
}

Integer getStartDurationSetting() {
	return safeToInt((settings ? settings["startDurationSetting"] : null), 10)
}

Integer getStopDurationSetting() {
	return safeToInt((settings ? settings["stopDurationSetting"] : null), 10)
}

Integer safeToInt(val, Integer defaultVal=0) {
	if ("${val}"?.isInteger()) {
		return "${val}".toInteger()
	}
	else if ("${val}".isDouble()) {
		return "${val}".toDouble()?.round()
	}
	else {
		return  defaultVal
	}
}

void logDebug(String msg) {
	if (settings?.debugLogging) {
		log.debug "$msg"
	}
}

def logsOff() {
	log.warn "Debug logging disabled."
	app.updateSetting("debugLogging", [value: "false", type: "bool"])
}
