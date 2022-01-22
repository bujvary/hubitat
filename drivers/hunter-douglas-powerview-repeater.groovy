/**
 *  Hunter Douglas PowerView Repeater
 *
 *  Copyright 2020 Brian Ujvary
 *
 *  This device driver is based on the work of Chris Lang and his SmartThings Hunter Douglas Powerview implementation
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
 *    01/21/2022 v2.3.0 - Version number update only
 *    01/20/2022 v2.2.0 - Version number update only
 *    01/19/2022 v2.1.0 - Version number update only
 *    10/08/2020 v1.2 - Modified to check for null value for brightness
 *    07/01/2020 v1.1 - Added firmware version to the Data section of Device Details
 *    05/10/2020 v1.0 - Initial release
 *
 */
metadata {
    definition(name: "Hunter Douglas PowerView Repeater", namespace: "hdpowerview", author: "Brian Ujvary", importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/hunter-douglas-powerview-repeater.groovy") {
        capability "Actuator"
        capability "Refresh"
        capability "Switch Level"
    }

    preferences {
        input name: "blinkEnabled", type: "bool", description: "", title: "Blink During Commands", defaultValue: false, required: false
        input name: "ledColor", type: "enum", description: "", title: "LED Color", displayDuringSetup: false, required: false, options: [
            1: "Red",
            2: "Purple",
            3: "Cyan",
            4: "Blue",
            5: "Green",
            6: "Tan",
            7: "White",
            8: "Off"
        ]
        input name: "logEnable", type: "bool", description: "", title: "Enable Debug Logging", defaultValue: true, required: false
    }
}

def installed() {
    initialize()
}

def initialize() {
    if (logEnable) runIn(900, logsOff)
}

def updated() {
    if (logEnable) log.debug "In updated()"

    def level = device.currentValue("level")
    def prefsMap = [: ]
    def rgbMap = getLedRGBMap()

    rgbMap << [brightness: level]
    prefsMap = [blinkEnabled: blinkEnabled, color: rgbMap]

    parent.setRepeaterPrefs(device, prefsMap)
}

public handleEvent(repeaterJson) {
    if (logEnable) log.debug "handleEvent: repeaterJson = ${repeaterJson}"

    def level = (repeaterJson.color.brightness != null) ? repeaterJson.color.brightness : 0
    if (level != device.currentValue("level"))
        sendEvent(name: "level", value: level)
    
    if (blinkEnabled != repeaterJson.blinkEnabled)
        device.updateSetting("blinkEnabled", [value: repeaterJson.blinkEnabled, type: "bool"])

    def colorEnum = getLedColorEnum(repeaterJson.color)
    if (ledColor != colorEnum)
        device.updateSetting("ledColor", [value: colorEnum, type: "enum"])
    
    device.updateDataValue("firmwareVersion", "${repeaterJson.firmware.revision}.${repeaterJson.firmware.subRevision}.${repeaterJson.firmware.build}")
}

def refresh() {
    parent?.pollRepeater(device)
}

def setLevel(level) {
    def colorMap = [: ]
    def rgbMap = getLedRGBMap()

    rgbMap << [brightness: level]
    colorMap = [color: rgbMap]

    parent.setRepeaterPrefs(device, colorMap)
}

private Map getLedRGBMap() {
    Map rgb = [: ]

    switch (ledColor.toInteger()) {
        case 1:
            rgb = ["red": 255, "green": 0, "blue": 0]
            break
        case 2:
            rgb = ["red": 153, "green": 0, "blue": 153]
            break
        case 3:
            rgb = ["red": 0, "green": 204, "blue": 204]
            break
        case 4:
            rgb = ["red": 0, "green": 0, "blue": 255]
            break
        case 5:
            rgb = ["red": 0, "green": 255, "blue": 0]
            break
        case 6:
            rgb = ["red": 255, "green": 190, "blue": 130]
            break
        case 7:
            rgb = ["red": 255, "green": 255, "blue": 255]
            break
        case 8:
            rgb = ["red": 0, "green": 0, "blue": 0]
            break
        default:
            rgb = ["red": 0, "green": 0, "blue": 0]
            break
    }

    return rgb
}

private String getLedColorEnum(colorMap) {
    String color

    colorMap.remove("brightness")

    if (colorMap == ["red": 255, "green": 0, "blue": 0])
        color = 1
    else if (colorMap == ["red": 153, "green": 0, "blue": 153])
        color = 2
    else if (colorMap == ["red": 0, "green": 204, "blue": 204])
        color = 3
    else if (colorMap == ["red": 0, "green": 0, "blue": 255])
        color = 4
    else if (colorMap == ["red": 0, "green": 255, "blue": 0])
        color = 5
    else if (colorMap == ["red": 255, "green": 190, "blue": 130])
        color = 6
    else if (colorMap == ["red": 255, "green": 255, "blue": 255])
        color = 7
    else if (colorMap == ["red": 0, "green": 0, "blue": 0])
        color = 8
    else
        color = 8

    return color
}

def logsOff() {
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
