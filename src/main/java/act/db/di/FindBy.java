package act.db.di;

/*-
 * #%L
 * ACT Framework
 * %%
 * Copyright (C) 2014 - 2017 ActFramework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import act.Act;
import act.app.ActionContext;
import act.app.App;
import act.db.Dao;
import act.handler.DelegateRequestHandler;
import act.handler.RequestHandler;
import act.handler.builtin.controller.RequestHandlerProxy;
import act.inject.param.ParamValueLoaderService;
import act.util.ActContext;
import org.osgl.$;
import org.osgl.inject.ValueLoader;
import org.osgl.mvc.result.NotFound;
import org.osgl.util.*;

import javax.validation.constraints.NotNull;
import java.util.Collection;

public class FindBy extends ValueLoader.Base {

    private String requestParamName;
    private String queryFieldName;
    private Dao dao;
    private StringValueResolver resolver;
    private boolean findOne;
    private boolean byId;
    private Class<?> rawType;
    private boolean notNull;

    @Override
    protected void initialized() {
        App app = App.instance();

        rawType = spec.rawType();
        notNull = spec.hasAnnotation(NotNull.class);
        findOne = !(Collection.class.isAssignableFrom(rawType));
        dao = app.dbServiceManager().dao(findOne ? rawType : (Class) spec.typeParams().get(0));

        queryFieldName = S.string(options.get("field"));
        byId = S.blank(queryFieldName) && (Boolean) options.get("byId");
        resolver = app.resolverManager().resolver(byId ? dao.idType() : (Class) options.get("fieldType"));
        if (null == resolver) {
            throw new IllegalArgumentException("Cannot find String value resolver for type: " + dao.idType());
        }
        requestParamName = S.string(value());
        if (S.blank(requestParamName)) {
            requestParamName = ParamValueLoaderService.bindName(spec);
        }
        if (!byId) {
            if (S.blank(queryFieldName)) {
                queryFieldName = requestParamName;
            }
        }
    }

    @Override
    public Object get() {
        ActContext ctx = ActContext.Base.currentContext();
        E.illegalStateIf(null == ctx);
        String value = resolve(requestParamName, ctx);
        if (S.blank(value)) {
            return ensureNotNull(null, "null");
        }
        Object by = resolver.resolve(value);
        ensureNotNull(by, value);
        if (null == by) {
            return null;
        }
        Collection col = findOne ? null : (Collection) App.instance().getInstance(rawType);
        if (byId) {
            Object bean = dao.findById(by);
            if (findOne) {
                return ensureNotNull(bean, value);
            } else {
                col.add(bean);
                return col;
            }
        } else {
            if (findOne) {
                Object found = dao.findOneBy(Keyword.of(queryFieldName).javaVariable(), by);
                return ensureNotNull(found, value);
            } else {
                col.addAll(C.list(dao.findBy(Keyword.of(queryFieldName).javaVariable(), by)));
                return col;
            }
        }
    }

    private Object ensureNotNull(Object obj, String value) {
        if (notNull) {
            if (null == obj) {
                if (!Act.isDev()) {
                    throw NotFound.get();
                }
                ActionContext ctx = ActionContext.current();
                if (null == ctx) {
                    throw NotFound.get();
                }
                RequestHandler handler = ctx.handler();
                if (handler instanceof DelegateRequestHandler) {
                    handler = ((DelegateRequestHandler) handler).realHandler();
                }
                if (handler instanceof RequestHandlerProxy) {
                    RequestHandlerProxy proxy = $.cast(handler);
                    throw proxy.notFoundOnMethod(S.fmt("%s not found by %s", spec.name(), value));
                }
                throw NotFound.get();
            }
        }
        return obj;
    }

    private static String resolve(String bindName, ActContext ctx) {
        String value = ctx.paramVal(bindName);
        if (S.notBlank(value)) {
            return value;
        }

        Keyword keyword = Keyword.of(bindName);
        if (keyword.tokens().size() > 1) {
            return resolve(keyword, ctx);
        } else {
            keyword = Keyword.of(bindName + " id");
            return resolve(keyword, ctx);
        }
    }

    private static String resolve(Keyword keyword, ActContext ctx) {
        String value = ctx.paramVal(keyword.underscore());
        if (S.notBlank(value)) {
            return value;
        }
        value = ctx.paramVal(keyword.javaVariable());
        if (S.notBlank(value)) {
            return value;
        }
        return null;
    }

}
