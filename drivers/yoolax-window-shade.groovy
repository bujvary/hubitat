/**
 *
 *	Copyright 2025 Brian Ujvary
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
 *  Ported from SmartThings to Hubitat by Brian Ujvary
 *
 *  Change Log:
 *    01/27/2025 v1.2  - Renamed to "Yoolax Window Shades"
 *                     - Reworked Invert Level/Percentage logic
 *    01/23/2025 v1.11 - Added support for Yoolax shade model TS0301
 *                     - Fixed issue in setLevel() where lift_pct was not initialized
 *    07/08/2022 v1.10 - Changed logsOff to run in 15 minutes
 *                     - Reverted to setLevel() in open()/close()
 *                     - Modified setLevel() to set level attribute to user requested level then use that
 *                       with current position to prevent toggling between opening/closing and partially
 *                       open states
 *                     - Adjusted battery level calculation to be more accurate
 *    07/04/2021 v1.9  - Removed hardcoded close() to setLevel(0)
 *    07/01/2021 v1.8  - Added definitions for off() and on(), hardcoded close() to setLevel(0)
 *    06/30/2021 v1.7  - Fixed Hubitat Dashboard tile colors update when open/closed/partially open
 *    06/07/2021 v1.6  - Updated capabilities to match Hubitat documentation
 *    04/28/2021 v1.5  - Added scheduled job to get battery status once an hour
 *    04/25/2021 v1.4  - Correct description text for battery level update
 *    04/23/2021 v1.3  - Added readAttribute for battery level to refresh()
 *    03/30/2021 v1.2  - Added preference and logic to set closed level of window shade
 *    03/30/2021 v1.1  - Removed descTextOutput preference and changed all logging to log on debugOutput
 *                     - Added preference and logic to invert the SetLevel level percentage if set to
 *                      handle shades that report the opposite of the percentage open
 *    03/28/2021 v1.0  - Initial release
 */
import groovy.json.JsonOutput
import hubitat.zigbee.zcl.DataType

metadata {
	definition(name: "Yoolax Window Shade", namespace: "bujvary", author: "Brian Ujvary", importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/yoolax-window-shade.groovy") {
		capability "Actuator"
		capability "Battery"
		capability "Configuration"
		capability "Refresh"
		capability "WindowShade"
		capability "HealthCheck"
		capability "Switch"
		capability "SwitchLevel"

		command "pause"
		command "setPresetPosition"
        
        attribute "deviceLevel", "Number"

		// IKEA
		fingerprint manufacturer: "IKEA of Sweden", model: "KADRILJ roller blind", deviceJoinName: "IKEA Window Treatment" // raw description 01 0104 0202 00 09 0000 0001 0003 0004 0005 0020 0102 1000 FC7C 02 0019 1000 //IKEA KADRILJ Blinds
		fingerprint manufacturer: "IKEA of Sweden", model: "FYRTUR block-out roller blind", deviceJoinName: "IKEA Window Treatment" // raw description 01 0104 0202 01 09 0000 0001 0003 0004 0005 0020 0102 1000 FC7C 02 0019 1000 //IKEA FYRTUR Blinds

		// Yookee yooksmart
		fingerprint inClusters: "0000,0001,0003,0004,0005,0102", outClusters: "0019", manufacturer: "Yookee", model: "D10110", deviceJoinName: "Yookee Window Treatment"
		fingerprint inClusters: "0000,0001,0003,0004,0005,0102", outClusters: "0019", manufacturer: "yooksmart", model: "D10110", deviceJoinName: "yooksmart Window Treatment"
		fingerprint inClusters: "0000,0001,0003,0004,0005,0020,0102", outClusters: "0003,0019", manufacturer: "yooksmart", model: "D10110", deviceJoinName: "yooksmart Window Treatment"
        fingerprint inClusters: "0000,0004,0001,0005,EF00,0003,0102", outClusters: "0019,000A", manufacturer: "_TZE200_jhhskent", model: "TS0301", deviceJoinName: "Yoolax Window Treatment"
	}

	preferences {
		input "invertSetLevel", "bool", title: "Invert Level/Position Percentage?", description: '<div><i>Invert the SetLevel or SetPosition percentage</i></div><br>', defaultValue: false
		input "preset", "number", title: "Preset position", description: "<div><i>Set the window shade preset position</i></div><br>", defaultValue: 50, range: "1..100", required: false, displayDuringSetup: false
		input "closedPosition", "number", title: " Closed position", description: "<div><i>Set the position for fully closed window shade</i></div><br>", defaultValue: 0, range: "0..100", required: false, displayDuringSetup: false
		input "debugOutput", "bool", title: "Enable debug logging?", description: '<div><i>Automatically disables after 15 minutes.</i></div><br>', defaultValue: true
	}
}

private getCLUSTER_WINDOW_COVERING() { 0x0102 }
private getCOMMAND_OPEN() { 0x00 }
private getCOMMAND_CLOSE() { 0x01 }
private getCOMMAND_PAUSE() { 0x02 }
private getCOMMAND_GOTO_LIFT_PERCENTAGE() { 0x05 }
private getATTRIBUTE_POSITION_LIFT() { 0x0008 }
private getATTRIBUTE_CURRENT_LEVEL() { 0x0000 }
private getCOMMAND_MOVE_LEVEL_ONOFF() { 0x04 }
private getBATTERY_PERCENTAGE_REMAINING() { 0x0021 }

private List<Map> collectAttributes(Map descMap) {
	List<Map> descMaps = new ArrayList<Map>()

	descMaps.add(descMap)

	if (descMap.additionalAttrs) {
		descMaps.addAll(descMap.additionalAttrs)
	}

	return descMaps
}

def installed() {
	if (debugOutput) log.debug "installed"
	sendEvent(name: "supportedWindowShadeCommands", value: JsonOutput.toJson(["open", "close", "pause"]))
	initialize()
}

def uninstalled() {
	if (debugOutput) log.debug "uninstalled"
	unschedule()
}

def initialize() {
	if (debugOutput) log.debug "initialize"
	unschedule()
	if (debugOutput) runIn(900,logsOff)
	schedule("0 0 0/1 1/1 * ? *", refreshBattery)
}

// Parse incoming device messages to generate events
def parse(String description) {
	if (debugOutput) log.debug "description:- ${description}"
    
	if (description?.startsWith("read attr -")) {
		Map descMap = zigbee.parseDescriptionAsMap(description)
		if (isBindingTableMessage(description)) {
			parseBindingTableMessage(description)
		} else if (supportsLiftPercentage() && descMap?.clusterInt == CLUSTER_WINDOW_COVERING && descMap.value) {
			if (debugOutput) log.debug "attr: ${descMap?.attrInt}, value: ${descMap?.value}, descValue: ${Integer.parseInt(descMap.value, 16)}, model: ${device.getDataValue("model")}"
			List<Map> descMaps = collectAttributes(descMap)
            
			def liftmap = descMaps.find { it.attrInt == ATTRIBUTE_POSITION_LIFT }
			if (liftmap && liftmap.value) {
                levelEventHandler(zigbee.convertHexToInt(liftmap.value))
			}
		} else if (!supportsLiftPercentage() && descMap?.clusterInt == zigbee.LEVEL_CONTROL_CLUSTER && descMap.value) {
			def valueInt = Math.round((zigbee.convertHexToInt(descMap.value)) / 255 * 100)
			levelEventHandler(valueInt)
		} else if (reportsBatteryPercentage() && descMap?.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && zigbee.convertHexToInt(descMap?.attrId) == BATTERY_PERCENTAGE_REMAINING && descMap.value) {
			def batteryLevel = zigbee.convertHexToInt(descMap.value)
			batteryPercentageEventHandler(batteryLevel)
		}
	}
}

def levelEventHandler(currentLevel) {
	def lastLevel = device.currentValue("deviceLevel")
	if (debugOutput) log.debug "levelEventHandle() currentLevel: ${currentLevel}, lastLevel: ${lastLevel}"
    
    unschedule();
 
    if (lastLevel == "undefined") {
	    if (debugOutput) log.debug "levelEventHandler() Ignore invalid level report"
    } else if (currentLevel == lastLevel) {
		if (debugOutput) log.debug "levelEventHandler() currentLevel = lastLevel"
	} else {
        sendEvent(name: "deviceLevel", value: currentLevel)
        sendEvent(name: "level", value: currentLevel)
        sendEvent(name: "position", value: currentLevel)

		if (lastLevel < currentLevel) {
			sendEvent([name: "windowShade", value: "opening"])
        } else if (lastLevel > currentLevel) {
			sendEvent([name: "windowShade", value: "closing"])
		}
	}
    
    runIn(3, "updateFinalState", [overwrite:true])
}

def updateFinalState() {
    def level = device.currentValue("deviceLevel")
    if (debugOutput) log.debug "updateFinalState() level: ${level}"

    if (level > 0 && level < 100) {
        sendEvent(name: "windowShade", value: "partially open")
        sendEvent(name: "switch", value: "on")
    }
    else {
        if (debugOutput) log.debug "updateFinalState() closedPosition: ${closedPosition}"
        sendEvent(name: "windowShade", value: level == closedPosition ? "closed" : "open")
        sendEvent(name: "switch", value: level == closedPosition ? "off" : "on")
    }
}

def batteryPercentageEventHandler(batteryLevel) {
	if (debugOutput) log.debug "batteryPercentageEventHandler() batteryLevel: ${batteryLevel}"
    
	if (batteryLevel != null) {
        if (isYooksmartOrYookee()) {
			batteryLevel = batteryLevel >> 1
		}
		batteryLevel = Math.min(100, Math.max(0, batteryLevel))
        
        if (debugOutput) log.debug "batteryPercentageEventHandler() Calculated batteryLevel: ${batteryLevel}"
        
        descriptionText = "${device.displayName} battery is ${batteryLevel}%"
		sendEvent([name: "battery", value: batteryLevel, unit: "%", descriptionText: descriptionText])
	}
}

def close() {
	if (debugOutput) log.info "close()"
	runIn(5, refresh)
	setLevel(0)
	//zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_CLOSE)
}

def open() {
	if (debugOutput) log.info "open()"
	runIn(5, refresh)
	setLevel(100)
	//zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_OPEN)
}

def off() {
	if (debugOutput) log.info "off()"
	close()
}

def on() {
	if (debugOutput) log.info "on()"
	open()
}

def setLevel(value, rate = null) {
	if (debugOutput) log.info "setLevel() level: ${value}"
	def cmd
    
	if (supportsLiftPercentage()) {
		lift_pct = value
        
		if (shouldInvertLiftPercentage()) {
			// some devices keeps % level of being closed (instead of % level of being opened)
			// inverting that logic is needed here
			lift_pct = 100 - value
        }
		cmd = zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(lift_pct.intValue(), 2))
	} else {
		cmd = zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, COMMAND_MOVE_LEVEL_ONOFF, zigbee.convertToHexString(Math.round(value * 255 / 100), 2))
	}

	return cmd
}

def setPosition(value){
	if (debugOutput) log.info "setPosition() level: ${value}"
	setLevel(value)
}

def pause() {
	if (debugOutput) log.info "pause()"
	// If the window shade isn't moving when we receive a pause() command then just echo back the current state for the mobile client.
	if (device.currentValue("windowShade") != "opening" && device.currentValue("windowShade") != "closing") {
		sendEvent(name: "windowShade", value: device.currentValue("windowShade"), isStateChange: true, displayed: false)
	}
	zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_PAUSE)
}

def setPresetPosition() {
	setLevel(preset ?: 50)
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	return refresh()
}

def refresh() {
	if (debugOutput) log.info "refresh()"
	def cmds
	if (supportsLiftPercentage()) {
		cmds = zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT)
	} else {
		cmds = zigbee.readAttribute(zigbee.LEVEL_CONTROL_CLUSTER, ATTRIBUTE_CURRENT_LEVEL)
	}
    
	if (reportsBatteryPercentage()) {
		cmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_PERCENTAGE_REMAINING)
	}
    
	return cmds
}

def refreshBattery() {
	if (debugOutput) log.info "refreshBattery()"
	def cmds
	if (reportsBatteryPercentage()) {
		cmds = zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_PERCENTAGE_REMAINING)
	}
    
	return cmds
}

def configure() {
	// Device-Watch allows 2 check-in misses from device + ping (plus 2 min lag time)
	if (debugOutput) log.info "configure()"
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    
	if (debugOutput) log.debug "Configuring Reporting and Bindings."

	def cmds
	if (supportsLiftPercentage()) {
		cmds = zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT, DataType.UINT8, 2, 600, null)
	} else {
		cmds = zigbee.levelConfig()
	}

	if (usesLocalGroupBinding()) {
		cmds += readDeviceBindingTable()
	}

	if (reportsBatteryPercentage()) {
		cmds += zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_PERCENTAGE_REMAINING, DataType.UINT8, 30, 21600, 0x01)
	}

	return refresh() + cmds
}

def usesLocalGroupBinding() {
	isIkeaKadrilj() || isIkeaFyrtur()
}

private def isBindingTableMessage(description) {   
	return false
}

private def parseBindingTableMessage(description) {
	Integer groupAddr = getGroupAddrFromBindingTable(description)
	if (groupAddr) {
		List cmds = addHubToGroup(groupAddr)
		cmds?.collect { new hubitat.device.HubAction(it) }
	}
}

private Integer getGroupAddrFromBindingTable(description) {
	if (debugOutput) log.info "Parsing binding table - '$description'"
	def btr = zigbee.parseBindingTableResponse(description)
	def groupEntry = btr?.table_entries?.find { it.dstAddrMode == 1 }
	if (debugOutput) log.info "Found ${groupEntry}"
	!groupEntry?.dstAddr ?: Integer.parseInt(groupEntry.dstAddr, 16)
}

private List addHubToGroup(Integer groupAddr) {
	["st cmd 0x0000 0x01 ${CLUSTER_GROUPS} 0x00 {${zigbee.swapEndianHex(zigbee.convertToHexString(groupAddr,4))} 00}", "delay 200"]
}

private List readDeviceBindingTable() {
	["zdo mgmt-bind 0x${device.deviceNetworkId} 0", "delay 200"]
}

def supportsLiftPercentage() {
	return isIkeaKadrilj() || isIkeaFyrtur() || isYooksmartOrYookee()
}

def shouldInvertLiftPercentage() {
	return isIkeaKadrilj() || isIkeaFyrtur() || invertSetLevel
}

def reportsBatteryPercentage() {
	return isIkeaKadrilj() || isIkeaFyrtur() || isYooksmartOrYookee()
}

def isIkeaKadrilj() {
	return device.getDataValue("model") == "KADRILJ roller blind"
}

def isIkeaFyrtur() {
	return device.getDataValue("model") == "FYRTUR block-out roller blind"
}

def isYooksmartOrYookee() {
	return device.getDataValue("model") == "D10110" || device.getDataValue("model") == "TS0301"
}

def updated() {
	initialize()
}

def logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
}
