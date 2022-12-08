/**
 *  Hunter Douglas PowerView Room
 *
 *  Copyright 2017 Chris Lang
 *
 *  Ported to Hubitat by Brian Ujvary
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
 *  Change Log:
 *    12/08/2022 v0.7 - Version number update only
 *    12/08/2022 v0.6 - Version number update only
 *    10/08/2022 v0.5 - Version number update only
 *    10/06/2022 v0.4 - Version number update only
 *    10/04/2022 v0.3 - Version number update only
 *    10/04/2022 v0.2 - Version number update only
 *    10/03/2022 v0.1 - Initial release
 *
 */
metadata {
    definition(name: "Hunter Douglas PowerView Room Gen3", namespace: "hdpowerview", author: "Chris Lang", importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/hunter-douglas-powerview-room-gen3.groovy") {
        capability "Actuator"
        capability "Momentary"
        capability "Switch"
        capability "Window Shade"
    }

    preferences {
        input("logEnable", "bool", title: "Enable logging", required: false, defaultValue: true)
    }
}

def installed() {
    initialize()
}

def initialize() {
    if (logEnable) runIn(900, logsOff)
}

// handle commands
def push() {
    sendEvent(name: "switch", value: "on", isStateChange: true, displayed: false)
    sendEvent(name: "switch", value: "off", isStateChange: true, displayed: false)
    sendEvent(name: "momentary", value: "pushed", isStateChange: true)
}

def on() {
    push()
    open()
}

def off() {
    push()
    close()
}

def open() {
    if (logEnable) log.debug "Executing 'open'"
    parent.openRoom(device)
}

def close() {
    if (logEnable) log.debug "Executing 'close'"
    parent.closeRoom(device)
}

def logsOff() {
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
