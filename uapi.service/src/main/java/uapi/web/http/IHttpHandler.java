/*
 * Copyright (C) 2010 The UAPI Authors
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at the LICENSE file.
 *
 * You must gained the permission from the authors if you want to
 * use the project into a commercial product
 */

package uapi.web.http;

import uapi.IIdentifiable;

/**
 * Created by xquan on 8/19/2016.
 */
public interface IHttpHandler extends IIdentifiable {

    String getUriMapping();

    void get(IHttpRequest request, IHttpResponse response);

    void put(IHttpRequest request, IHttpResponse response);

    void post(IHttpRequest request, IHttpResponse response);

    void delete(IHttpRequest request, IHttpResponse response);
}
