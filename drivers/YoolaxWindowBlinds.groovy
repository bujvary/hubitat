/**
 *
 *  Copyright (C) 2021 Matt Werline
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 *
 *  --> IMPORTANT: Use the configure button after device is added to Hubitat <--
 *
 *
 *  Note about Driver Functionality (Please Read)
 *    This driver by default is enabled to support the Yoolax Shangri-la Sheer Shades. If you do not have this style of shade from Yoolax you will want
 *    to disable the “Enable Shangri-la Sheer Shade Functions” setting in the driver preferences. This will disable the Midpoint setting and logic. 
 *
 *    For Shangri-la Sheer Shades, when enabled, the “Midpoint” preference should be set to the level on your shades in which the sheer blades are fully open.
 *    The open/close commands will stop at the midpoint level if they are not already at that level. A subsequent issuance of the open/close will fully 
 *    open/close the shades. If you wish to bypass the midpoint, use the “hardOpen” or “hardClose” command in your automatons. You can also directly set the 
 *    shades to the shear open state (Midpoint) by calling the “setMidpoint” command.
 *
 *    For all types of shades that the reported shade levels from the driver can be different from the actually physical level (deviceLevel attribute). 
 *    If the shades are at the close, open, or midpoint (Shangri-la only) it will report 0 or 100 level. This is done to prevent some controllers, like HomeKit, 
 *    from reporting the blinds are still opening/closing.
 *
 *    The Settings maxReportingLift and maxReportingBattery adjust the max amount of time before the shades will report their level and battery state.
 *    The default values should be OK for most, but if you are seeing battery drain try increasing these values, starting with the maxReportingLift as 
 *    it defaults for 600 seconds (10 Minutes). Besure to use the configuration button after saving a change to these values as they will not be updated
 *    on the device until the configruation command is executed.
 *
 *  Modified by Brian Ujvary
 *
 *  Change Log:
 *    01/24/2025 v1.1 - Added support for Yoolax model TS0301
 *                    - Modified updateFinalState() to set windowShade to 'partially open' state
 *                      if shade is not fully opened or closed
 *                    - Changed sheerShadeFlag default to false
 */
import hubitat.zigbee.zcl.DataType

metadata {
    definition(name: "Yoolax Window Blinds", namespace: "mwerline", author: "Matt Werline", importUrl: "https://raw.githubusercontent.com/mwerline/hubitat/main/YoolaxWindowBlinds.groovy") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Window Shade"
        capability "Health Check"
        capability "Switch Level"
        capability "Battery"

        command "pause"
        command "hardOpen"
        command "hardClose"
        command "setMidpoint"

       	attribute "lastCheckin", "String"
        attribute "lastOpened", "String"
        attribute "deviceLevel", "Number"

        fingerprint deviceJoinName: "Yoolax Motorized Window Blinds", model: "D10110", profileId: "0104", endpointId: 01, inClusters: "0000,0001,0003,0004,0005,0020,0102", outClusters: "0003,0019", manufacturer: "yooksmart"
        fingerprint deviceJoinName: "Yoolax Motorized Window Blinds", model: "TS0301", profileId: "0104", endpointId: 01, inClusters: "0000,0004,0001,0005,EF00,0003,0102", outClusters: "0019,000A", manufacturer: "_TZE200_jhhskent"
    }
    
    preferences { 
        input name: "openLevel", type: "number", defaultValue: 100, range: "0..100", title: "Max open level", description: "Percentage used for the Shade's Fully Opened Level"    
        input name: "closeLevel", type: "number", defaultValue: 0, range: "0..100", title: "Closed level", description: "Percentage used for the Shade's Fully Closed Level"    
        input name: "midLevel", type: "number", defaultValue: 50, range: "0..100", title: "Midpoint level", description: "Percentage used for the Shade's Midpoint Level (For Shangri-la Sheer Shades Only)"    

        input name: "maxReportingLift", type: "number", defaultValue: 600, range: "0..86400", title: "Zigbee Level Max Report Time (Seconds)", description: "Advanced Setting - Zigbee Max Report Time in Seconds for Shade Level"
        input name: "maxReportingBattery", type: "number", defaultValue: 21600, range: "0..86400", title: "Zigbee Battery Max Report Time (Seconds)", description: "Advanced Setting - Zigbee Max Report Time in Seconds for Shade Battery Level"


        input name: "sheerShadeFlag", type: "bool", title: "Enable Shangri-la Sheer Shade Functions", defaultValue: false
        input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
		input name: "descTextOutput", type: "bool", title: "Enable descriptionText logging?", defaultValue: true
    }
}

private getCLUSTER_BATTERY_LEVEL() { 0x0001 }
private getCLUSTER_WINDOW_COVERING() { 0x0102 }
private getCOMMAND_OPEN() { 0x00 }
private getCOMMAND_CLOSE() { 0x01 }
private getCOMMAND_PAUSE() { 0x02 }
private getCOMMAND_GOTO_LIFT_PERCENTAGE() { 0x05 }
private getATTRIBUTE_POSITION_LIFT() { 0x0008 }
private getATTRIBUTE_CURRENT_LEVEL() { 0x0000 }
private getCOMMAND_MOVE_LEVEL_ONOFF() { 0x04 }
private getBATTERY_PERCENTAGE_REMAINING() { 0x0021 }

// Utility function to Collect Attributes from event
private List<Map> collectAttributes(Map descMap) {
	List<Map> descMaps = new ArrayList<Map>()

	descMaps.add(descMap)

	if (descMap.additionalAttrs) {
		descMaps.addAll(descMap.additionalAttrs)
	}

	return descMaps
}

// Parse incoming device reports to generate events
def parse(String description) {
    if (debugOutput) log.debug "Parse report description:- '${description}'."
    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)

    //  Send Event for device heartbeat    
    sendEvent(name: "lastCheckin", value: now)
    
    // Parse Event
    if (description?.startsWith("read attr -")) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        
        // Zigbee Window Covering Event
        if (descMap?.clusterInt == CLUSTER_WINDOW_COVERING && descMap.value) {
            if (debugOutput) log.debug "attr: ${descMap?.attrInt}, value: ${descMap?.value}, descValue: ${Integer.parseInt(descMap.value, 16)}, ${device.getDataValue("model")}"

            // Parse Attributes into a List
            List<Map> descMaps = collectAttributes(descMap)
            
            // Get the Current Shade Position
            def liftmap = descMaps.find { it.attrInt == ATTRIBUTE_POSITION_LIFT }
            if (liftmap && liftmap.value) levelEventHandler(zigbee.convertHexToInt(liftmap.value))
        } else if (descMap?.clusterInt == CLUSTER_BATTERY_LEVEL && descMap.value) {
            if(descMap?.value) {
                batteryLevel = Integer.parseInt(descMap.value, 16)
                batteryLevel = convertBatteryLevel(batteryLevel)
                if (debugOutput) log.debug "attr: '${descMap?.attrInt}', value: '${descMap?.value}', descValue: '${batteryLevel}'."
                sendEvent(name: "battery", value: batteryLevel)
            } else {
                if (debugOutput) log.debug "failed to parse battery level attr: '${descMap?.attrInt}', value: '${descMap?.value}'."
            }
        }
    }
}

// Convert Battery Level to (0-100 Scale)
def convertBatteryLevel(rawValue) {
    def batteryLevel = rawValue - 50
    batteryLevel = batteryLevel * 100
	batteryLevel = batteryLevel.intdiv(150)
    return batteryLevel
}

// Handle Level Change Reports
def levelEventHandler(currentLevel) {
    def lastLevel = device.currentValue("deviceLevel")
    if (debugOutput) log.debug "levelEventHandler - currentLevel: '${currentLevel}' lastLevel: '${lastLevel}'."

    if (lastLevel == "undefined" || currentLevel == lastLevel) { 
        // Ignore invalid reports
        if (debugOutput) log.debug "undefined lastLevel"
        runIn(3, "updateFinalState", [overwrite:true])
    } else {
        setReportedLevel(currentLevel)
        if (currentLevel == 0 || currentLevel <= closeLevel) {
            sendEvent(name: "windowShade", value: currentLevel == closeLevel ? "closed" : "open")
        } else {
            if (lastLevel < currentLevel) {
                sendEvent([name:"windowShade", value: "opening"])
            } else if (lastLevel > currentLevel) {
                sendEvent([name:"windowShade", value: "closing"])
            }
        }
    }
    if (lastLevel != currentLevel) {
        if (debugOutput) log.debug "newlevel: '${newLevel}' currentlevel: '${currentLevel}' lastlevel: '${lastLevel}'."
        runIn(1, refresh)
    }
}

// Modify the reported level to compensate for Homekit assuming values other than 0 or 100 mean the shades are still opening/closing
def setReportedLevel(rawLevel) {
   sendEvent(name: "deviceLevel", value: rawLevel)
   if(rawLevel == closeLevel) {
       sendEvent(name: "level", value: 0)
       sendEvent(name: "position", value: 0)
   } else if (rawLevel ==  midLevel && sheerShadeFlag) {
       sendEvent(name: "level", value: 10)
       sendEvent(name: "position", value: 10)       
   } else if (rawLevel ==  openLevel) {
       sendEvent(name: "level", value: 100)
       sendEvent(name: "position", value: 100)       
   } else {
       sendEvent(name: "level", value: rawLevel)
       sendEvent(name: "position", value: rawLevel)              
   }
}

def updateFinalState() {
    def level = device.currentValue("deviceLevel")
    if (debugOutput) log.debug "Running updateFinalState: '${level}'."
    
    if (level > closeLevel && level < openLevel) {
        sendEvent(name: "windowShade", value: "partially open")
        sendEvent(name: "switch", value: "on")
    } else {
        sendEvent(name: "windowShade", value: level == closeLevel ? "closed" : "open")
        sendEvent(name: "switch", value: level == closeLevel ? "off" : "on")
    }
}
                 
// Open Blinds Command
def open() {
    def currentLevel = device.currentValue("deviceLevel")
    if(currentLevel >= openLevel) {
        if (descTextOutput) log.info "Blinds are already Fully Opened."
    } else if(sheerShadeFlag == false) {
        hardOpen()
    } else if(currentLevel == midLevel) {
        hardOpen()
    } else if(currentLevel > midLevel && currentLevel < closeLevel) {
        hardOpen()
    } else {
        setMidpoint()
    }
}

// Hard Open Blinds Command
def hardOpen() {
    if (descTextOutput) log.info "Fully Opening the Blinds."
    setHardLevel(openLevel)
}

// Close Blinds Command
def close() {
    if (descTextOutput) log.info "Closing the Blinds."
    def currentLevel = device.currentValue("deviceLevel")
    if(currentLevel == closeLevel) {
        if (descTextOutput) log.info "Blinds are already Fully Closed."
    } else if(sheerShadeFlag == false) {
        hardClose()
    } else if(currentLevel == midLevel) {
        hardClose()
    } else if(currentLevel > closeLevel) {
        setMidpoint()
    } else {
        hardClose()
    }
}

// Hard Close Blinds Command
def hardClose() {
    if (descTextOutput) log.info "Fully Closing the Blinds."
    setHardLevel(closeLevel)
}

// Midpoint Command
def setMidpoint() {
    if (descTextOutput) log.info "Moving Blinds to MidPoint Value."
    setHardLevel(midLevel)
}

// Set Level Command
def setHardLevel(value) {
    if (debugOutput) log.debug "Setting the Blinds to level '${value}'."
    value = value.toInteger()
    runIn(5, refresh)
    return zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(openLevel.toInteger() - value, 2))
}

// Set Position Command
def setPosition(value) {
    if (descTextOutput) log.info "Setting the Blinds to level '${value}'."
    setHardLevel(value)
}

// Set Level Command
def setLevel(value, rate = null) {
    if (descTextOutput) log.info "Setting the Blinds to level '${value}'."
    setHardLevel(value)
}

// Return Level adjusted based on the Min/Max settings
def restrictLevelValue(value) {
    return value
}

// Pause the blinds
def pause() {
    if (descTextOutput) log.info "Pausing the Blinds."
    zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_PAUSE)
}

// Stop Postition Change
def stopPositionChange() {
    pause()
}

// Start Postition Change
def startPositionChange(direction) {
    if(direction == "open") {
        open()
    } else if (direction == "close") {
        close()
    }
}

// Refresh the current state of the blinds
def refresh() {
    if (debugOutput) log.debug "Running refresh()"
    return zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT) + zigbee.readAttribute(CLUSTER_BATTERY_LEVEL, BATTERY_PERCENTAGE_REMAINING)
}

// Configure Device Reporting and Bindings
def configure() {
    if (descTextOutput) log.info "Configuring Device Reporting and Bindings."
    sendEvent(name: "checkInterval", value: 7320, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    def cmds = zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT, DataType.UINT8, 0, maxReportingLift.toInteger(), 0x01) + zigbee.configureReporting(CLUSTER_BATTERY_LEVEL, 0x0021, DataType.UINT8, 600, maxReportingBattery.toInteger(), 0x01)
    return refresh() + cmds
}

// Driver Update Event
def updated() {
    if (debugOutput) log.debug "Running updated()"
    unschedule()

    // Disable logging automaticly after 30 minutes
    if (debugOutput) {
        runIn(1800,logsOff)
        log.debug "Debug loging is currently enabled. Will automaticly be disabled in 30 minutes."
    }
}

// Automaticly Disable Debug logging Event Handeler
def logsOff(){
	log.info "Debug Automaticly Disabled."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
}
