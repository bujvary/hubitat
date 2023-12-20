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
 *    12/20/2023 v2.6.8 - Added checks for color array and blinkEnabled value in JSON from repeater before processing
 *    04/03/2023 v2.6.7 - Version number update only
 *    10/09/2022 v2.6.6 - Version number update only
 *    10/07/2022 v2.6.5 - Version number update only
 *    10/06/2022 v2.6.4 - Version number update only
 *    10/06/2022 v2.6.3 - Version number update only
 *    09/08/2022 v2.6.2 - Version number update only
 *    06/24/2022 v2.6.1 - Version number update only
 *    06/23/2022 v2.6.0 - Version number update only
 *    01/25/2022 v2.5.0 - Remove LED color preference and replace with setColor command 
 *    01/24/2022 v2.4.0 - Version number update only
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
        
        command(
             "setColor", 
             [
                [
                     "name":"LED Color",
                     "description":"Set the LED color of the repeater",
                     "type":"ENUM",
                     "constraints": getColorOptions()
                ]
             ]
        );
        
        attribute "colorName", "string"
    }

    preferences {
        input name: "blinkEnabled", type: "bool", description: "", title: "Blink During Commands", defaultValue: false, required: false
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

    def prefsMap = [blinkEnabled: blinkEnabled]
    parent.setRepeaterPrefs(device, prefsMap)
}

public handleEvent(repeaterJson) {
    if (logEnable) log.debug "handleEvent: repeaterJson = ${repeaterJson}"

    if (repeaterJson?.color != null) {
        def level = (repeaterJson.color.brightness != null) ? repeaterJson.color.brightness : 0
        if (level != device.currentValue("level"))
            sendEvent(name: "level", value: level)
    
        def colorName = rgbToColorName(repeaterJson.color)
        if (colorName != device.currentValue("colorName"))
            sendEvent(name: "colorName", value: colorName)
    }
    else {
        if (logEnable) log.debug "repeaterJson is missing color array"
    }
    
    if (repeaterJson?.blinkEnabled != null) {
        if (repeaterJson.blinkEnabled != blinkEnabled)
            device.updateSetting("blinkEnabled", [value: repeaterJson.blinkEnabled, type: "bool"])
    }
    else {
        if (logEnable) log.debug "repeaterJson is missing blinkEnabled value"
    }

    device.updateDataValue("firmwareVersion", "${repeaterJson.firmware.revision}.${repeaterJson.firmware.subRevision}.${repeaterJson.firmware.build}")
}

def refresh() {
    parent?.pollRepeater(device)
}

def setLevel(level) {
    if (logEnable) log.debug "setLevel() level = $level"
    
    def colorMap = [:]
    def rgbMap = colorNameToRgb(device.currentValue("colorName"))

    rgbMap << [brightness: level]
    colorMap = [color: rgbMap]

    parent.setRepeaterPrefs(device, colorMap)
}

def setColor(color) {
    if (logEnable) log.debug "setColor() color = $color"
    
    def colorMap = [:]
    def rgbMap = colorNameToRgb(color)

    rgbMap << [brightness: device.currentValue("level")]
    colorMap = [color: rgbMap]

    parent.setRepeaterPrefs(device, colorMap)
}

private Map colorNameToRgb(colorName) {
    if (logEnable) log.debug "colorNameToRgb() colorName = $colorName"
    
    Map rgb = [:]

    switch (colorName) {
        case "Red":
            rgb = ["red": 255, "green": 0, "blue": 0]
            break
        case "Purple":
            rgb = ["red": 153, "green": 0, "blue": 153]
            break
        case "Cyan":
            rgb = ["red": 0, "green": 204, "blue": 204]
            break
        case "Blue":
            rgb = ["red": 0, "green": 0, "blue": 255]
            break
        case "Green":
            rgb = ["red": 0, "green": 255, "blue": 0]
            break
        case "Tan":
            rgb = ["red": 255, "green": 190, "blue": 130]
            break
        case "White":
            rgb = ["red": 255, "green": 255, "blue": 255]
            break
        case "Off":
            rgb = ["red": 0, "green": 0, "blue": 0]
            break
        default:  //Off
            rgb = ["red": 0, "green": 0, "blue": 0]
            break
    }

    return rgb
}

private String rgbToColorName(rgbColor) {
    if (logEnable) log.debug "rgbToColorName() rgbColor = $rgbColor"
    
    String colorName

    rgbColor.remove("brightness")
    if (rgbColor == ["red": 255, "green": 0, "blue": 0])
        colorName = "Red"
    else if (rgbColor == ["red": 153, "green": 0, "blue": 153])
        colorName = "Purple"
    else if (rgbColor == ["red": 0, "green": 204, "blue": 204])
        colorName = "Cyan"
    else if (rgbColor == ["red": 0, "green": 0, "blue": 255])
        colorName = "Blue"
    else if (rgbColor == ["red": 0, "green": 255, "blue": 0])
        colorName = "Green"
    else if (rgbColor == ["red": 255, "green": 190, "blue": 130])
        colorName = "Tan"
    else if (rgbColor == ["red": 255, "green": 255, "blue": 255])
        colorName = "White"
    else if (rgbColor == ["red": 0, "green": 0, "blue": 0])
        colorName = "Off"
    else
        colorName = "Off"

    return colorName
}

def getColorOptions() {
    return ["Red", "Purple", "Cyan", "Blue", "Green", "Tan", "White", "Off"]
}

def logsOff() {
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
