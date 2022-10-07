/**
 *  Hunter Douglas PowerView Scene
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
 *    10/07/2022 v2.6.5 - Version number update only
 *    10/06/2022 v2.6.4 - Version number update only
 *    10/06/2022 v2.6.3 - Version number update only
 *    09/08/2022 v2.6.2 - Version number update only
 *    06/24/2022 v2.6.1 - Version number update only
 *    06/23/2022 v2.6.0 - Version number update only
 *    01/25/2022 v2.5.0 - Version number update only
 *    01/24/2022 v2.4.0 - Version number update only
 *    01/21/2022 v2.3.0 - Version number update only
 *    01/20/2022 v2.2.0 - Version number update only
 *    01/19/2022 v2.1.0 - Version number update only
 *    05/10/2020 v1.0 - Initial release
 *
 */
metadata {
    definition(name: "Hunter Douglas PowerView Scene", namespace: "hdpowerview", author: "Chris Lang", importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/hunter-douglas-powerview-scene.groovy") {
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
def presetPosition() {
    if (logEnable) log.debug "Executing 'presetPosition'"
    parent.triggerSceneFromDevice(device)
}

def push() {
    sendEvent(name: "switch", value: "on", isStateChange: true, displayed: false)
    sendEvent(name: "switch", value: "off", isStateChange: true, displayed: false)
    sendEvent(name: "momentary", value: "pushed", isStateChange: true)

    presetPosition()
}

def on() {
    push()
}

def off() {
    push()
}

def logsOff() {
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
