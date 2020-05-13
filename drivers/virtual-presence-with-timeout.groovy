/**
 *  ****************  Virtual Presence with Timeout  ****************
 *
 *  Design Usage:
 *  This driver is a virtual presence with an adjustable departure timeout.
 *
 *  Copyright 2019 Brian Ujvary
 *
 *  Based on work by Ryan Casler, Warren Poschman
 *  
 *  This driver is free and you may do as you like with it.  
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *
 *  Changes:
 *
 *  1.0.4 - Updated the importURL
 *  1.0.3 - Added logic to store rssi value and include in event description text
 *  1.0.2 - Added parse() and handlePresenceEvent(), other code cleanup
 *  1.0.1 - Change lastCheckin to device state
 *  1.0.0 - Initial release
 */

metadata {
    definition (name: "Virtual Presence with Timeout",
                namespace: "bujvary",
                author: "Brian Ujvary",
                importURL: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/virtual-presence-with-timeout.groovy") {
        
        capability "Sensor"
        capability "Presence Sensor"
       
    }
 
    preferences {
        input name: "checkInterval", type: "enum", title: "Presence timeout (minutes)",
            defaultValue:"2", options: ["2", "3", "5", "10", "15"]
        input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
    }
    
    command "arrived"
    command "departed"
}

def arrived() {
    if (logEnable) log.debug "In ${device.displayName}.arrived(): present"
    sendEvent(name: "presence", value: "present")
}

def departed() {
    if (logEnable) log.debug "In ${device.displayName}.departed(): not present"
    sendEvent(name: "presence", value: "not present")
}

def updated() {
    if (logEnable) log.debug "In ${device.displayName}.updated()"
    stopTimer()
    startTimer()
}

def installed() {
    log.info "In ${device.displayName}.installed()"
    updated()
}

def uninstalled() {
}

def initialize() {
    if (logEnable) runIn(900,logsOff)
}

def parse(rssi) {
    state.lastCheckin = now()
    state.rssi = rssi
    
    handlePresenceEvent(true)
    return
}

def logsOff(){
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

private handlePresenceEvent(present) {
    if (logEnable) log.debug "In ${device.displayName}.handlePresenceEvent()"
    
    def wasPresent = device.currentState("presence")?.value == "present"
    
    if (!wasPresent && present) {
        log.debug "Sensor is present"
        startTimer()
    } else if (!present) {
        log.debug "Sensor is not present"
        stopTimer()
    } else if (wasPresent && present) {
        return   
    }
    
    def linkText = device.displayName
    def descriptionText
    
    if ( present )
        descriptionText = "${linkText} has arrived (rssi = ${state.rssi})"
    else
        descriptionText = "${linkText} has left (rssi = ${state.rssi})"
    	
    def eventMap = [
          name: "presence",
          value: present ? "present" : "not present",
          linkText: linkText,
          descriptionText: descriptionText,
          translatable: true
        ]
        
    log.debug "Creating presence event: ${device.displayName} ${eventMap.name} is ${eventMap.value}"
    sendEvent(eventMap)
}

private startTimer() {
    log.debug "In ${device.displayName}.startTimer() Scheduling periodic timer"
    runEvery1Minute("checkPresenceCallback")
}

private stopTimer() {
    log.debug "In ${device.displayName}.stopTimer() Stopping periodic timer"
    unschedule()
}

private checkPresenceCallback() {
    if (logEnable) log.debug "In ${device.displayName}.checkPresenceCallback()"
    if (logEnable) log.debug "now: " + now() + ", lastCheckin: " + state.lastCheckin
    
    def timeSinceLastCheckin = (now() - state.lastCheckin ?: 0) / 1000
    def theCheckInterval = (checkInterval ? checkInterval as int : 2) * 60
    
    log.debug "${device.displayName} Sensor checked in ${timeSinceLastCheckin} seconds ago (theCheckInterval = ${theCheckInterval})"
    
    if (timeSinceLastCheckin >= theCheckInterval) {
        handlePresenceEvent(false)
    }
}