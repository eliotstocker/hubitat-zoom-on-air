/**
 *  Zoom On Air
 *
 *  Copyright 2022 Eliot Stocker
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
definition(
        name: "Zoom On Air",
        namespace: "tv.piratemedia",
        author: "Eliot Stocker",
        description: "Sync a device with your zoom calls",
        category: "Convenience",
        iconUrl: "https://raw.githubusercontent.com/eliotstocker/SmartThings-LightPhysicalControl/master/logo-small.png",
        iconX2Url: "https://raw.githubusercontent.com/eliotstocker/SmartThings-LightPhysicalControl/master/logo.png"
)

preferences {
    page(name: "prefInit")
    page(name: "prefSettings")
    page(name: "prefAPISettings")
}

def prefInit() {
    if (app.getInstallationState() == "COMPLETE") {
        return prefSettings()
    }
    
    dynamicPage(name: "prefSettings", title: "Zoom On Air Initial Setup", uninstall: false, install: true) {
        section("Device Details"){
            input "devices", "capability.switch", title: "Devices to Control", multiple: true
        }
    }
}

def prefSettings() {
    dynamicPage(name: "prefSettings", title: "Zoom On Air Settings", uninstall: true, install: true) {
        section("Device Details") {
            input "devices", "capability.switch", title: "Devices to Control", multiple: true, submitOnChange: true
            if(checkForCapability("ChangeLevel")) {
                input "level", "number", title: "Light Brightness", range: "1..100", required: false
            }
            if(checkForCapability("ColorControl")) {
                input "color", "color", title: "Color", required: false
            }
            //input "resume", "bool", title: "Return to previous state after meeting"
        }
        section("Zoom API") {
            paragraph(installAppButton())
            href(name: "prefAPISettings", title: "Zoom API settings", required: false, page: "prefAPISettings", description: "Setup or Edit Zoom API Connection")
        }
    }
}

def prefAPISettings() {
    dynamicPage(name: "prefAPISettings", title: "Zoom API Settings", uninstall: false, install: false) {
        section() {
            paragraph(instructions())
        }
        section("Settings"){
            input "clientId", "string", title: "App Client ID", multiple: false
            input "clientSecret", "string", title: "App Client Secret", multiple: false
            input "secretToken", "string", title: "Secret Token", multiple: false
            input "email", "string", title: "Account Email Address", multiple: false
        }
    }
}

def instructions() {
    return "<span style=\"font-size: 0.75em\">To use this application setup a zoom oauth application <a href=\"https://marketplace.zoom.us/develop/create\">here</a><br />" +
        "Click create on OAuth App, and use the folllowing details:<br />" +
        "<b>App Name:</b> Anything (we recomend Zoom On Air)<br />" +
        "<b>App Type:</b> User-managed App<br />" +
        "<b>Publish App:</b> Off<br />" +
        "Click Create<br />" +
        "<br />" +
        "In the next Screen enter the following details:<br />" +
        "<b>Redirect URL:</b> " + getRedirectEndpoint() + "<br />" +
        "<b>Add allow list:</b> " + getRedirectEndpoint() + "<br />" +
        "Now copy the Client ID and Client Secret into the boxes bellow<br />" +
        "<br />" +
        "In the side navigation select <b>Feature</b><br />" + 
        "Enable <b>Event Subscriptions</b>, click <b>Add Event Subscription</b> and enter the following:<br />" + 
        "<b>Subscription name:</b> User Status<br />" + 
        "<b>Event notification endpoint URL</b>: " + getWebhookEndpoint() + "<br />" +
        "Click <b>Add Events</b>" +
        "<br />" +
        "In the popup navigate to the <b>User Activity</b> pane and tick: <b>User's presence status has been updated</b><br />" +
        "Click <b>Done</b>" +
        "Copy the <b>Secret Token</b> to the corresponding box bellow</span><br />" +
        "<br />" +
        "You will also need to fill in various required fields in the <b>Information</b> pane, these will not effect the funtion of the application and can be filled with anything you wish..."
}

def installAppButton() {
    def encodedRedirect = java.net.URLEncoder.encode(getRedirectEndpoint(), "UTF-8")
    def url = "https://zoom.us/oauth/authorize?response_type=code&client_id=${clientId}&redirect_uri=${encodedRedirect}"
    
    if(clientId == null || clientId == "" || clientSecret == null || clientSecret == "" || secretToken == null || secretToken == "" || email == null || email == "") {
        return "Please Setup all Zoom API parameters using the button bellow, you will then be able to install the Zoom Application"
    }
    
    def opacity = state.zoomInstalled ? "0.75" : "1"
    
    def button = "<a href=\"${url}\"><div style=\"background: #eeeeee; opacity: ${opacity}; border-radius: 17px 3px 3px 17px; margin: 0 -8px; box-shadow: rgba(0, 0, 0, 0.14) 0px 2px 2px 0px, rgba(0, 0, 0, 0.2) 0px 3px 1px -2px, rgba(0, 0, 0, 0.12) 0px 1px 5px 0px;\"><img style=\"padding-right: 12px;width: 60px;\" src=\"https://explore.zoom.us/media/desktop-logo-zoombrand.png\"><span style=\"color: #111111\">Install Zoom App</span></div></a>"
    
    if(state.zoomInstalled) {
        return "<div style=\"font-size: 0.75em\">Zoom App Installed, you may reinstall if required with the button bellow:</div><br />" + button
    }
    
    return button
}

def getRedirectEndpoint() {
    return "${getFullApiServerUrl()}/redirect?access_token=${state.accessToken}";
}

def getWebhookEndpoint() {
    return "${getFullApiServerUrl()}/receive?access_token=${state.accessToken}";
}

def installed() {
    state.onAir = false;
    try {
        if (!state.accessToken) {
            createAccessToken()
        }
    }
    catch (ex) {
        log.warn "OAUTH is not enabled under the \"OAuth\" button in the app code editor.  This is required to use the app"
    }
}

mappings {
    path("/receive") {
        action: [
            POST: "receiveWebhook",
            GET: "validateWebhook"
        ]
    }
    path("/redirect") {
        action: [
            GET: "appInstallRedirect"
        ]
    }
}

def appInstallRedirect() {
    if (params.code) {
        state.zoomInstalled = true
    }
    
    def location = getFullLocalApiServerUrl().replace("/apps/api/", "/installedapp/configure/")
    def html = "<html>" +
        "<head>" + 
        "<title>App Installed</title>" +
        "</head>" +
        "<body>" +
        "<h1>Zoom On Air for Hubitat installed</h1><br />" + 
        "please wait..." +
        "<script>setTimeout(() => {location.href = \"${location}\"}, 1000)</script>" +
        "</body>" +
        "</html>"
    render contentType: "text/html", data: html, status: 200 
}

def validateRequestSignature(request) {
    log.debug request.headers
    if(request.headers && request.headers['X-zm-request-timestamp'] && request.headers['X-zm-signature']) {
        def message = "v0:${request.headers['X-zm-request-timestamp'][0]}:${request.body}"
        
        def hash = hashValue(message);
        
        log.debug "actual: ${request.headers['X-zm-signature']}; computed: v0=${hash}"
        
        if(request.headers['X-zm-signature'][0] == "v0=${hash}") {
            return true
        }
    } else {
        log.error "missing validation headers"
    }
    return false
}

def receiveWebhook() {
    log.debug("webhook reception ocurred: ${request.body}")
    def valid = validateRequestSignature(request)
    
    if(!valid) {
        log.error "signature did not mtach expected value"
        return renderError(401, "invalid or missing request signature");
    }
    
    def json
    try {
        json = parseJson(request.body)
    }
    catch (e) {
        log.error "JSON received from app is invalid! ${request.body}"
        return renderError(400, "JSON received from app is invalid! ${request.body}")
    }
    
    if(json.event == "endpoint.url_validation") {
        def hash = hashValue(json.payload.plainToken);
        def payload = [
            plainToken: json.payload.plainToken,
            encryptedToken: hash
        ]
        
        return renderResponse(payload)
    }
    
    if(json.event == "user.presence_status_updated") {
        receievePresenceEvent(json.payload)
    }
    
    data = [
        received: true
    ]
    
    return renderResponse(data)
}

def hashValue(payload) {
    def mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(new javax.crypto.spec.SecretKeySpec(secretToken.getBytes("UTF-8"), "HmacSHA256"));
    
    return mac.doFinal(payload.getBytes("UTF-8")).encodeHex().toString();
}

def validateWebhook() {
    log.debug("webhook validation ocurred")
    return renderResponse([valid: true])
}

def receievePresenceEvent(payload) {
    if(payload.object.email != email) {
        log.error "email of presence detected did not match provided email"
        return
    }
    
    if(payload.object.presence_status == "In_Meeting" || payload.object.presence_status == "Presenting") {
        state.onAir = true
        devices.on()
        setAttributes()
    } else if(state.onAir) {
        log.debug payload.object.presence_status
        state.onAir = false
        devices.off()
    }
}

def renderResponse(data) {
    render contentType: "application/json", data: new groovy.json.JsonOutput().toJson(data), status: 200
}

def renderError(status, message) {
    render contentType: "application/json", data: new groovy.json.JsonOutput().toJson([error: true, message: message]), status: status
}

def setAttributes() {
    devices.each { device ->
        setAttributes(device)
    }
}

import hubitat.helper.ColorUtils
def setAttributes(device) {
    if(color && color != "#000000" && checkDeviceForCapability(device, "ColorControl")) {
        def rgb = ColorUtils.hexToRGB(color)
        def hsv = ColorUtils.rgbToHSV(rgb)
        def colorMap = [hue: hsv[0], saturation: hsv[1], level: level ? level : hsv[2]]
        devices.setColor(colorMap)
    } else if(checkDeviceForCapability(device, "ChangeLevel")) {
        devices.setLevel(lState.level, 0)
    }
}

def checkDeviceForCapability(dev, capability) {
    def capabilites = dev.getCapabilities()
    return capabilites.any { it.name == capability }
}
 
def checkForCapability(capability) {
    return devices.any { device ->
        device.getCapabilities().any { it.name == capability }
    }
}
