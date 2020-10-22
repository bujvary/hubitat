/**
 *  NUT UPS Driver for Hubitat Elevation v1.4
 *
 *  10/21/2020 - v1.4 - Added "UPS Name" preference to allow for monitoring of a specific UPS
 *                      if the NUT server monitors multiple UPS units.
 *                    - Changed server hostname, port and UPS Name to be required.
 *
 *  Child driver for the NUT UPS Monitor app.  A child UPS device is created automatically after installing and
 *  configuring the settings in the NUT UPS Monitor application.
 *
 *  Copyright 2019 ritchierich
 *
 *	Credits:
 *	Special thanks to Peter Gulyas (@guyeeba) for the original driver integrating a NUT server with HE
 *  Special thanks to Stephan Hackett for testing this driver
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.transform.Field

@Field List stateVariables = ["battery.charge","battery.mfr.date","battery.runtime","ups.alarm","ups.status"]

metadata {
	definition (name: "NUT UPS Device", namespace: "HubitatCommunity", author: "ritchierich") {
		capability "Initialize"
		capability "Telnet"
		capability "Refresh"
		capability "PowerSource"
		capability "VoltageMeasurement"		
		capability "Battery"
	}
	
	attribute "battery", "String"
	attribute "deviceAlarm", "String"
	attribute "batteryRuntime", "String"
	attribute "upsStatus", "String"

	preferences {
        input name: "nutServerHost", type: "text", description: "IP or hostname of NUT server", title: "NUT server hostname", required: true
        input name: "nutServerPort", type: "number", description: "Port number of NUT server", title: "NUT server port number", defaultValue: 3493, range: "1..65535", required: true
        input name: "nutUpsName", type: "text", description: "Name of UPS to poll", title: "UPS Name", required: true
        input name: "nutPollingInterval", type: "number", description: "Polling interval", title: "Polling interval", defaultValue: 10, range: "1..600"
		input name: "displayAllVariables", type: "bool", title: "Display all variables?", defaultValue: false, required: false
		input name: "failedThreshold", type: "number", description: "Pause connection after this number of failed connections", title: "Connection Failure Threshold", defaultValue: 10
		input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
		input name: "pauseUpdates", type: "bool", title: "Pause updates", defaultValue: false, required: false
    }
}

def installed(){
	if (isDebugEnabled == null) {
		device.updateSetting("isDebugEnabled",[value:"false",type:"bool"])	
	}
	
	if (pauseUpdates == null) {
		device.updateSetting("pauseUpdates",[value:"false",type:"bool"])	
	}
	
	if (displayAllVariables == null) {
		device.updateSetting("displayAllVariables",[value:"false",type:"bool"])	
	}
	
	initialize()
}

def updated(){
	unschedule()
	telnetClose()
	initialize()
}

def initialize(){
	if (failedThreshold == null) {
		device.updateSetting("failedThreshold",[value:"10",type:"number"])	
	}
	
	if (!pauseUpdates) {
		state.remove("batteryDate")
		state.remove("connectionErrorCount")
		state.lastPollTime = ""
		
		if (displayAllVariables) {
			state.remove("batteryRuntime")
			state.upsData = [:]
		} else {
			state.remove("upsData")
		}

        if (state.upsName != nutUpsName) {
            state.remove("upsName")
        }
        
		refresh()
	}
}

def refresh() {
	if (!pauseUpdates) {
		try {
			telnetConnect([termChars:[10]], nutServerHost, nutServerPort.toInteger(), null, null)
			if (state.connectionErrorCount != null) {
				state.remove("connectionErrorCount")
			}
		} catch (err) {
			if (state.connectionErrorCount == null) {
				state.connectionErrorCount = 1	
			} else if (state.connectionErrorCount < failedThreshold) {
				state.connectionErrorCount += 1
			} else if (state.connectionErrorCount >= failedThreshold) {
				state.connectionErrorCount += 1
				device.updateSetting("pauseUpdates",[value:"true",type:"bool"])
				log.error "Refresh telnet connection error reached threshold of ${failedThreshold}, pausing updates: ${err}"
				return
			}
			
			log.error "Refresh telnet connection error ${state.connectionErrorCount} of ${failedThreshold}: ${err}"
			if (state.upsName) {
				runIn(nutPollingInterval, refresh)
			}
		}

		if (!state.upsName) {
			sendCommand("LIST UPS")
			runIn(nutPollingInterval, refresh)
		}
		if (state.upsName) {
			if (displayAllVariables) {
				sendCommand("LIST VAR ${state.upsName}")
			} else {
				for (int i=0;i < stateVariables.size(); i++) {
					sendCommand("GET VAR ${state.upsName} ${stateVariables[i]}")
				}
			}
			runIn(nutPollingInterval, refresh)
		}
		sendCommand("LOGOUT")
	}
}

def parse(String message) {
	logDebug "Received: ${message}"
	
	String[] msg = message.split("\"?( |\$)(?=(([^\"]*\"){2})*[^\"]*\$)\"?")
    if (msg[0] == "UPS") {
        if (msg[1] == nutUpsName) {
    	    state.upsName = msg[1]
            logDebug "state.upsName set to ${state.upsName}"
        }
    } else if (message == "END LIST UPS") {
        if (state.upsName) {
            refresh()
        } else {
            log.error("Failed to find UPS Name ${nutUpsName} on NUT server. Pausing updates.")
            device.updateSetting("pauseUpdates",[value:"true",type:"bool"])
        }
    } else if (message == "BEGIN LIST VAR " + state.upsName) {
        // Do nothing
    } else if (msg[0] == "VAR") {
    	parseValues(msg.drop(2))
    } else if (message == "OK Goodbye") {
    	setLastPoll()
    } else if (message == "END LIST VAR " + state.upsName) {
    	setLastPoll()
    }
}

def setLastPoll() {
	def nowDateTime = new java.text.SimpleDateFormat("MM/dd hh:mm a")
	nowDateTime.setTimeZone(location.timeZone)
	nowDateTime = nowDateTime.format(new Date()).toString()
	state.lastPollTime = nowDateTime
}

def parseValues(String[] msg) {
	def result = []
	def key = msg[0]
	def value = msg.length > 1 ? msg[1] : null
	
	if (displayAllVariables) {
		// If no values have changed stop parsing to minimize logging
		if (state.upsData.containsKey(key) && state.upsData.get(key) == value) {
			return
		} else {
			logDebug "Value changed key: ${key}, from: ${state.upsData.get(key)}, to: ${value}"
			state.upsData["${key}".toString()] = value
		}
	}

	switch (key) {
		case "battery.charge":
			result << createEvent(name: 'battery', value: value, unit: "%",	descriptionText: "Battery is at ${value}%")
			break;
		case "battery.mfr.date":
			state.batteryDate = value
			break;
		case "battery.runtime":
			// The runtime value changes quite frequently based on UPS load.  To minimize logging, set device attbitute to "mains" while on mains power, but reflect actual in a state variable
			def runtime = (Integer.parseInt(value) / 60) + " minutes"
			state.batteryRuntime = runtime
			
			if (device.currentValue("powerSource") == "mains" && device.currentValue("batteryRuntime") != "mains") {
				result << createEvent(name: 'batteryRuntime', value: "mains", descriptionText: "Remaining runtime is ${runtime}")
			} else if (device.currentValue("powerSource") != "mains") {
				result << createEvent(name: 'batteryRuntime', value: runtime, descriptionText: "Remaining runtime is ${runtime}")
			}
			break;
		case "ups.alarm":
			result << createEvent(name: 'deviceAlarm', value: value, descriptionText: "Device alarm is ${value}")
			break;
		case "ups.status":
			def statuses = value.split(" ")
			def powerSource = "unknown"
			if (statuses.contains('OL')) {
				powerSource = "mains"
			} else if (statuses.contains('OB')) {
				powerSource = "battery"
			}

			result << createEvent(name: 'powerSource', value: powerSource, descriptionText: "Power source is ${powerSource}")

			if (!statuses.contains('ALARM') && device.currentValue("deviceAlarm") != "All Clear") {
				logDebug "Alarm no longer detected."
				result << createEvent(name: 'deviceAlarm', value: 'All Clear', descriptionText: "Alarm removed.")
			}

			def statusCodeMap = [
				'OL': 'Online',
				'OB': 'On Battery',
				'LB': 'Low Battery',
				'HB': 'High Battery',
				'RB': 'Battery Needs Replaced',
				'CHRG': 'Battery Charging',
				'DISCHRG': 'Battery Discharging',
				'BYPASS': 'Bypass Active',
				'CAL': 'Runtime Calibration',
				'OFF': 'Offline',
				'OVER': 'Overloaded',
				'TRIM': 'Trimming Voltage',
				'BOOST': 'Boosting Voltage',
				'FSD': 'Forced Shutdown',
				'ALARM': 'Alarm'
			]

			String statusText = statuses?.collect { statusCodeMap[it] }.join(" - ")
			result << createEvent(name: 'upsStatus', value: statusText, descriptionText: "Device status is ${statusText}")
			break;
	}
	
	return result
}

def telnetStatus(String status) {
	// Exlude "Stream is closed" messages since logout causes this message
	if (status != "receive error: Stream is closed") {
		log.error("telnetStatus: ${status}")
	}
}

def sendCommand(cmd) {
	logDebug "sendCommand - Sending ${cmd}"
	return sendHubCommand(new hubitat.device.HubAction("${cmd}", hubitat.device.Protocol.TELNET))
}

private logDebug(msg) {
	if (isDebugEnabled) {
		log.debug "$msg"
	}
}
