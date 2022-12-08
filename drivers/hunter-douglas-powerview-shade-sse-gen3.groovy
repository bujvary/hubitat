/**
 * Hunter Douglas Powerview Shade SSE
 *
 *  Copyright 2022 Brian Ujvary
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
 * =======================================================================================
 *
 *  This driver is based on the work of Robert Morris - CoCoHue Bridge for Hubitat
 *
 *  Change Log:
 *    12/08/2022 v0.6 - Added check in parse() to verify message was a shade event before processing
 *    10/08/2022 v0.5 - Initial release
 *
 */ 

import groovy.transform.Field
import groovy.json.JsonSlurper

// Number of seconds to wait after Bridge EventStream (SSE) is disconnected before consider it so on Hubitat
// Seems to be helpful at the moment because get spurious disconnects when SSE is working fine, shortly followed
// by a reconnect (~6 sec for me, so 7 should cover most)
@Field static final Integer eventStreamDisconnectGracePeriod = 8

@Field static final Integer httpPort = 80

metadata {
   definition(name: "Hunter Douglas PowerView Shade SSE Gen3", namespace: "hdpowerview", author: "Brian Ujvary", importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/hunter-douglas-powerview-shade-sse-gen3.groovy") {
      capability "Actuator"
      capability "Initialize"
       
      command "connectEventStream"
      command "disconnectEventStream"
       
      attribute "status", "STRING"
      attribute "eventStreamStatus", "STRING"
   }
   
   preferences() {
        input name: 'logEnable', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 15 minutes</i></div><br>', displayDuringSetup: false, defaultValue: true
   }   
}

def installed() {
    log.debug "installed()"
    
    initialize()
}

def updated() {
    log.debug "updated()"
    
    initialize()
}

def initialize() {
    log.debug "initialize()"
    
    if (logEnable) runIn(900, logsOff)
    
    if (parent.getShadeEventStreamEnabledSetting()) 
        connectEventStream()
}

def connectEventStream() {
    if (logEnable) log.debug "connectEventStream()"
    
    if (parent.getShadeEventStreamEnabledSetting() != true) {
        log.warn "Hunter Douglas Powerview app is configured not to use EventStream. To reliably use this interface, it is recommended to enable this option in the app."
    }
    
    def gatewayIp = parent.getGatewayIP()
    
    if (logEnable) log.debug "Connecting to event stream at 'http://${gatewayIp}:${httpPort}/home/shades/events'"

    interfaces.eventStream.connect(
        "http://${gatewayIp}:${httpPort}/home/shades/events", [
            headers: ["Accept": "text/event-stream"],
            rawData: true,
            pingInterval: 10,
            readTimeout: 3600,
            ignoreSSLIssues: true
        ]
    )
}

def reconnectEventStream(Boolean notIfAlreadyConnected = true) {
    if (logEnable) log.debug "reconnectEventStream(notIfAlreadyConnected=$notIfAlreadyConnected)"
    
    if (device.currentValue("eventStreamStatus") == "connected" && notIfAlreadyConnected) {
        if (logEnable) log.debug "already connected; skipping reconnection"
    }
    else if (parent.getShadeEventStreamEnabledSetting() != true) {
        if (logEnable) log.debug "skipping reconnection because (parent) app configured not to use EventStream"
    }
    else {
        connectEventStream()
    }
}

def disconnectEventStream() {
    if (logEnable) log.debug "disconnectEventStream()"
    
    interfaces.eventStream.close()
}

def eventStreamStatus(String message) {
    if (logEnable) log.debug "eventStreamStatus: $message"
    
    if (message.startsWith("START:")) {
        setEventStreamStatusToConnected()
    }
    else {
        runIn(eventStreamDisconnectGracePeriod, "setEventStreamStatusToDisconnected")
    }
}

private void setEventStreamStatusToConnected() {
    if (logEnable) log.debug "setEventStreamStatusToConnected()"
    
    parent.setShadeEventStreamStatus(true)
    
    unschedule("setEventStreamStatusToDisconnected")
    
    if (device.currentValue("eventStreamStatus") == "disconnected")
        doSendEvent("eventStreamStatus", "connected")
    
    state.connectionRetryTime = 3
}

private void setEventStreamStatusToDisconnected() {
    if (logEnable) log.debug "setEventStreamStatusToDisconnected()"
    
    parent.setShadeEventStreamStatus(false)
    
    doSendEvent("eventStreamStatus", "disconnected")
    
    if (state.connectionRetryTime) {
        state.connectionRetryTime *= 2
        if (state.connectionRetryTime > 900) {
            state.connectionRetryTime = 900 // cap retry time at 15 minutes
        }
    }
    else {
       state.connectionRetryTime = 5
    }
    
    if (logEnable) log.debug "reconnecting SSE in ${state.connectionRetryTime}"
    
    runIn(state.connectionRetryTime, "reconnectEventStream")
}

def parse(String description) {
    if (logEnable) log.debug "parse: $description"

    shadeEvent = new JsonSlurper().parseText(description)
    
    if (shadeEvent?.evt != null) {
        def shadeDevice = parent.getShadeDevice(shadeEvent.id)        
        if (shadeDevice != null)
            shadeDevice.handleSseEvent(shadeEvent)
    }
}

def doSendEvent(String eventName, eventValue) {
    if (logEnable) log.debug "doSendEvent($eventName, $eventValue)"
    
    String descriptionText = "${device.displayName} ${eventName} is ${eventValue}}"
    sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText)
}

def logsOff() {
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
