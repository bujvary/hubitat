/*
 *  Vehicle v1.0.0	(Drivers Code)
 *
 *  Changelog:
 *
 *    1.0 (10/20/2021)
 *      - Initial Release
 *
 *
 *  Copyright 2021 Brian Ujvary
 *
 *  This driver is based on the work of Kevin LaFramboise - Zooz Garage Door Opener for Hubitat
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

metadata {
	definition (
		name: "Vehicle",
		namespace: "bujvary",
		author: "Brian Ujvary",
		importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/vehicle.groovy"
	) {
		capability "Actuator"
		capability "Switch"
        
		command "start"
		command "stop"
		command "lock"
	}

	preferences {
		input name: 'logEnable', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 15 minutes</i></div><br>', defaultValue: true
	}
}


def installed() {
    logDebug "installed()..."
	initialize()
}

def updated() {
	logDebug "updated()..."
	initialize()
}

private initialize() {
    logDebug "initialize()..."
    if (logEnable) runIn(900, logsOff)
}


def parse(String description) {
	logDebug "parse(description)..."
}

def parse(Map evt) {
    logDebug "parse(map)..."
    
	if (evt) {
		evt.descriptionText = evt.descriptionText ?: "${device.displayName} - ${evt.name} is ${evt.value}"
		
		if (evt.displayed) {
			logDebug "${evt.descriptionText}"
		}		
		sendEvent(evt)
	}
}

def on() {
	logDebug "on()..."
    start()
}

def off() {
	logDebug "off()..."
    stop()
}

def start() {
	logDebug "start()..."
	parent.childStart(device.deviceNetworkId)
}

def stop() {
	logDebug "stop()..."
	parent.childStop(device.deviceNetworkId)
}

def lock() {
	logDebug "lock()..."
	parent.childLock(device.deviceNetworkId)
}

def logDebug(msg) {
	if (settings?.logEnable) {
		log.debug "$msg"
	}
}

def logsOff() {
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
