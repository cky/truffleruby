/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.interop;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import org.truffleruby.RubyLanguage;
import org.truffleruby.language.RubyBaseWithoutContextNode;

@GenerateUncached
public abstract class ForeignToRubyArgumentsNode extends RubyBaseWithoutContextNode {

    public static ForeignToRubyArgumentsNode create() {
        return ForeignToRubyArgumentsNodeGen.create();
    }

    public abstract Object[] executeConvert(Object[] args);

    @ExplodeLoop
    @Specialization(guards = "args.length == cachedArgsLength", limit = "getLimit()")
    public Object[] convertCached(Object[] args,
            @Cached("args.length") int cachedArgsLength,
            @Cached ForeignToRubyNode foreignToRubyNode) {
        final Object[] convertedArgs = new Object[cachedArgsLength];

        for (int n = 0; n < cachedArgsLength; n++) {
            convertedArgs[n] = foreignToRubyNode.executeConvert(args[n]);
        }

        return convertedArgs;
    }

    @Specialization(replaces = "convertCached")
    public Object[] convertUncached(Object[] args,
            @Cached ForeignToRubyNode foreignToRubyNode) {
        final Object[] convertedArgs = new Object[args.length];

        for (int n = 0; n < args.length; n++) {
            convertedArgs[n] = foreignToRubyNode.executeConvert(args[n]);
        }

        return convertedArgs;
    }

    protected int getLimit() {
        return RubyLanguage.getCurrentContext().getOptions().INTEROP_CONVERT_CACHE;
    }

}
