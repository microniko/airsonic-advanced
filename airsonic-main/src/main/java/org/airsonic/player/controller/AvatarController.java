/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.domain.Avatar;
import org.airsonic.player.domain.AvatarScheme;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;

/**
 * Controller which produces avatar images.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/avatar")
public class AvatarController {

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private ResourceLoader loader;

    /**
    private long getLastModified(Avatar avatar, String username) {
        long result = avatar == null ? -1L : avatar.getCreatedDate().toEpochMilli();

        if (username != null) {
            UserSettings userSettings = settingsService.getUserSettings(username);
            result = Math.max(result, userSettings.getChanged().toEpochMilli());
        }

        return result;
    }
    */

    @GetMapping
    public void handleRequest(
            @RequestParam(required = false) Integer id,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "false") boolean forceCustom,
            HttpServletResponse response) throws Exception {

        Avatar avatar = getAvatar(id, username, forceCustom);

        if (avatar == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType(avatar.getMimeType());
        Resource res = loader.getResource(avatar.getPath().toString());
        if (!res.exists()) {
            res = loader.getResource("file:" + avatar.getPath().toString());
        }
        IOUtils.copy(res.getInputStream(), response.getOutputStream());
    }

    private Avatar getAvatar(Integer id, String username, boolean forceCustom) {

        if (id != null) {
            return settingsService.getSystemAvatar(id);
        }

        if (username == null) {
            return null;
        }

        UserSettings userSettings = settingsService.getUserSettings(username);
        if (userSettings.getAvatarScheme() == AvatarScheme.CUSTOM || forceCustom) {
            return settingsService.getCustomAvatar(username);
        }
        if (userSettings.getAvatarScheme() == AvatarScheme.NONE) {
            return null;
        }
        return settingsService.getSystemAvatar(userSettings.getSystemAvatarId());
    }

}
