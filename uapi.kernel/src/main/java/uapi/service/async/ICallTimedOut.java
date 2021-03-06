/*
 * Copyright (C) 2010 The UAPI Authors
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at the LICENSE file.
 *
 * You must gained the permission from the authors if you want to
 * use the project into a commercial product
 */

package uapi.service.async;

/**
 * The call back interface used for the specified call timed out.
 */
public interface ICallTimedOut {

    /**
     * Invoke this method after the call is timed out
     *
     * @param   callId
     *          The call id
     */
    void accept(String callId);
}
