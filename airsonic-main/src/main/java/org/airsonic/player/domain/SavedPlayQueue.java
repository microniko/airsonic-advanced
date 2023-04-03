/*
 * This file is part of Airsonic.
 *
 *  Airsonic is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Airsonic is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Copyright 2015 (C) Sindre Mehus
 */

package org.airsonic.player.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Used to save the play queue state for a user.
 * <p/>
 * Can be used to share the play queue (including currently playing track and position within
 * that track) across client apps.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
@AllArgsConstructor
@Getter
@Setter
public class SavedPlayQueue {

    private Integer id;
    private String username;
    private List<Integer> mediaFileIds;
    private Integer currentMediaFileId;
    private Long positionMillis;
    private Instant changed;
    private String changedBy;

}
