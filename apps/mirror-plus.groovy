/*************
*  Device Mirror Plus
*  Copyright 2021 Terrel Allen All Rights Reserved
*
*  This program is free software: you can redistribute it and/or modify
*  it under the terms of the GNU Affero General Public License as published
*  by the Free Software Foundation, either version 3 of the License, or
*  (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*  GNU Affero General Public License for more details.
*
*  You should have received a copy of the GNU Affero General Public License
*  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*
*  WARNING!!!
*  Use at your own risk.
*  Modify at your own risk.
*
*  USAGE
*  Mirror one device with another.
*
*  CHANGE LOG
*  v202110022251
*      -add hours and minutes to change log dates
*  v202110020000
*      -re-add iconUrl
*      -re-add iconX2Url
*  v202110010000
*      -add header
*  v202109280000
*      -initial release w/ battery level, temperature, humidity, etc...
*
*************/


definition(
    name: "Device Mirror Plus",
    namespace: "whodunitGorilla",
    author: "Terrel Allen",
    singleInstance: true,
    description: "Mirror a master device to slave device(s)",
    category: "Convenience",
    importUrl: "https://gitlab.com/terrelsa13/device-mirror-plus/-/raw/master/Mirror_Plus.groovy",
    iconUrl: "",
    iconX2Url: "")

preferences {
    page(name: "mainPage")
}

def mainPage(){
    dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
        section ("Device Mirror Plus"){
            app(name: "childApps1", appName: "Device Mirror Plus Child", namespace: "whodunitGorilla", title: "Create New Device Mirror", submitOnChange: true, multiple: true)
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    log.debug "there are ${childApps.size()}"
    childApps.each {child ->
        log.debug "chld app: ${child.label}"
    }
}

// checks for endless looping (max iterations are 4)
def checkSlavesExist(def slaves, def master, def iterations, Boolean b){
    // check if no children exist for current parent app
    if(childApps.size() == 0){
        for(slave in slaves){
            if(slave == master){
                if(iterations <= 0){
                    return true
                }
                else{
                    if(checkSlavesExist(slaves, master, iterations-1, b)){
                        return true
                    }
                }
            }
        }
    }


    for(child in childApps){
        for(slave in slaves){
            if(child.getMasterId() == slave || master == slave){
                log.debug "masterChild: ${child.getMasterId()}  master: ${master}   slave: ${slave}"
                if(iterations <= 0){
                    return true
                }
                else{s
                    log.warn "Iterations: ${iterations}"
                    if(checkSlavesExist(slaves, master, iterations-1, b)){
                        return true
                    }
                }
            }
        }
    }
    return b
}
