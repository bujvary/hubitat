/**
 *
 *	Copyright 2019 SmartThings
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
 *  Ported to Hubitat by Brian Ujvary
 *
 *  Change Log:
 *    03/30/2021 v1.1 - Removed descTextOutput preference and changed all logging to log on debugOutput
 *                    - Added preference and logic to invert the SetLevel level percentage if set to
 *                      handle shades that report the opposite of the percentage open
 *    03/28/2021 v1.0 - Initial release
 */
import groovy.json.JsonOutput
import hubitat.zigbee.zcl.DataType

metadata {
	definition(name: "ZigBee Window Shade with Battery", namespace: "smartthings", author: "SmartThings", ocfDeviceType: "oic.d.blind", mnmn: "SmartThings", vid: "generic-shade-3") {
		capability "Actuator"
		capability "Battery"
		capability "Configuration"
		capability "Refresh"
		capability "Window Shade"
		capability "Health Check"
		capability "Switch Level"

	    command "pause"
        command "setPresetPosition"

		// IKEA
		fingerprint manufacturer: "IKEA of Sweden", model: "KADRILJ roller blind", deviceJoinName: "IKEA Window Treatment" // raw description 01 0104 0202 00 09 0000 0001 0003 0004 0005 0020 0102 1000 FC7C 02 0019 1000 //IKEA KADRILJ Blinds
		fingerprint manufacturer: "IKEA of Sweden", model: "FYRTUR block-out roller blind", deviceJoinName: "IKEA Window Treatment" // raw description 01 0104 0202 01 09 0000 0001 0003 0004 0005 0020 0102 1000 FC7C 02 0019 1000 //IKEA FYRTUR Blinds

		// Yookee yooksmart
		fingerprint inClusters: "0000,0001,0003,0004,0005,0102", outClusters: "0019", manufacturer: "Yookee", model: "D10110", deviceJoinName: "Yookee Window Treatment"
		fingerprint inClusters: "0000,0001,0003,0004,0005,0102", outClusters: "0019", manufacturer: "yooksmart", model: "D10110", deviceJoinName: "yooksmart Window Treatment"
        fingerprint inClusters: "0000,0001,0003,0004,0005,0020,0102", outClusters: "0003,0019", manufacturer: "yooksmart", model: "D10110", deviceJoinName: "yooksmart Window Treatment"
	}

	preferences {
        input "invertSetLevel", "bool", title: "Invert Level/Position Percentage?", description: '<div><i>Invert the SetLevel or SetPosition percentage</i></div><br>', defaultValue: false
        input "preset", "number", title: "Preset position", description: "<div><i>Set the window shade preset position</i></div><br>", defaultValue: 50, range: "1..100", required: false, displayDuringSetup: false
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
}

// Parse incoming device messages to generate events
def parse(String description) {
	if (debugOutput) log.debug "description:- ${description}"
	if (description?.startsWith("read attr -")) {
		Map descMap = zigbee.parseDescriptionAsMap(description)
		if (isBindingTableMessage(description)) {
			parseBindingTableMessage(description)
		} else if (supportsLiftPercentage() && descMap?.clusterInt == CLUSTER_WINDOW_COVERING && descMap.value) {
			if (debugOutput) log.debug "attr: ${descMap?.attrInt}, value: ${descMap?.value}, descValue: ${Integer.parseInt(descMap.value, 16)}, ${device.getDataValue("model")}"
			List<Map> descMaps = collectAttributes(descMap)
			def liftmap = descMaps.find { it.attrInt == ATTRIBUTE_POSITION_LIFT }
			if (liftmap && liftmap.value) {
				def newLevel = zigbee.convertHexToInt(liftmap.value)
				if (shouldInvertLiftPercentage()) {
					// some devices report % level of being closed (instead of % level of being opened)
					// inverting that logic is needed here to avoid a code duplication
					newLevel = 100 - newLevel
				}
				levelEventHandler(newLevel)
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
	def lastLevel = device.currentValue("level")
	if (debugOutput) log.debug "levelEventHandle - currentLevel: ${currentLevel} lastLevel: ${lastLevel}"
	if (lastLevel == "undefined" || currentLevel == lastLevel) { //Ignore invalid reports
		if (debugOutput) log.debug "Ignore invalid reports"
	} else {
		sendEvent(name: "level", value: currentLevel)
        sendEvent(name: "position", value: currentLevel)
		if (currentLevel == 0 || currentLevel == 100) {
			sendEvent(name: "windowShade", value: currentLevel == 0 ? "closed" : "open")
		} else {
			if (lastLevel < currentLevel) {
				sendEvent([name: "windowShade", value: "opening"])
			} else if (lastLevel > currentLevel) {
				sendEvent([name: "windowShade", value: "closing"])
			}
			runIn(1, "updateFinalState", [overwrite:true])
		}
	}
}

def updateFinalState() {
	def level = device.currentValue("level")
	if (debugOutput) log.debug "updateFinalState: ${level}"
	if (level > 0 && level < 100) {
		sendEvent(name: "windowShade", value: "partially open")
	}
}

def batteryPercentageEventHandler(batteryLevel) {
	if (batteryLevel != null) {
		batteryLevel = Math.min(100, Math.max(0, batteryLevel))
		sendEvent([name: "battery", value: batteryLevel, unit: "%", descriptionText: "{{ device.displayName }} battery was {{ value }}%"])
	}
}

def close() {
    if (debugOutput) log.info "close()"
	//setLevel(100)
    runIn(5, refresh)
    zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_CLOSE)
}

def open() {
	if (debugOutput) log.info "open()"
	//setLevel(0)
    runIn(5, refresh)
    zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_OPEN)
}

def setLevel(data, rate = null) {
    if (debugOutput) log.info "setLevel() level = ${data}"
	def cmd
	if (supportsLiftPercentage()) {
		if (shouldInvertLiftPercentage() || invertSetLevel) {
			// some devices keeps % level of being closed (instead of % level of being opened)
			// inverting that logic is needed here
			data = 100 - data
		}      
		cmd = zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(data.intValue(), 2))
	} else {
		cmd = zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, COMMAND_MOVE_LEVEL_ONOFF, zigbee.convertToHexString(Math.round(data * 255 / 100), 2))
	}
	return cmd
}

def setPosition(value){
    if (debugOutput) log.info "setPosition() level = ${value}"
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
	return isIkeaKadrilj() || isIkeaFyrtur()
}

def reportsBatteryPercentage() {
	return isIkeaKadrilj() || isIkeaFyrtur()
}

def isIkeaKadrilj() {
	return device.getDataValue("model") == "KADRILJ roller blind"
}

def isIkeaFyrtur() {
	return device.getDataValue("model") == "FYRTUR block-out roller blind"
}

def isYooksmartOrYookee() {
	return device.getDataValue("model") == "D10110"
}

def updated() {
	unschedule()
	if (debugOutput) runIn(1800,logsOff)
}

def logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
}
