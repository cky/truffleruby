/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Test;
import org.truffleruby.fixtures.FluidForce;
import org.truffleruby.shared.TruffleRuby;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PolyglotInteropTest {

    @Test
    public void testCreateContext() {
        try (Context polyglot = Context.create()) {
            assertEquals(14, polyglot.eval(TruffleRuby.LANGUAGE_ID, "14").asInt());
        }
    }

    @Test
    public void testParameters() {
        try (Context polyglot = Context.create()) {
            assertEquals(16, polyglot.eval("ruby", "lambda { |a, b| a + b }").execute(14, 2).asInt());
        }
    }

    @Test
    public void testCallingMethods() {
        try (Context polyglot = Context.create()) {
            assertEquals(0.909, polyglot.eval("ruby", "Math").getMember("sin").execute(2).asDouble(), 0.01);
        }
    }

    @Test
    public void testPassingBlocks() {
        try (Context polyglot = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            final int[] counter = new int[]{0};

            polyglot.eval("ruby", "lambda { |block| (1..3).each { |n| block.call n } }")
                    .execute(polyglot.asValue((IntConsumer) n -> counter[0] += n));

            assertEquals(6, counter[0]);
        }
    }

    @Test
    public void testCreatingObjects() {
        // Native access needed for ENV['TZ']
        try (Context polyglot = Context.newBuilder().allowNativeAccess(true).build()) {
            assertEquals(2021, polyglot.eval("ruby", "Time").newInstance(2021, 3, 18).getMember("year").execute().asInt());
        }
    }

    @Test
    public void testAccessingArrays() {
        try (Context polyglot = Context.create()) {
            assertEquals(4, polyglot.eval("ruby", "[3, 4, 5]").getArrayElement(1).asInt());
            assertEquals(4, polyglot.eval("ruby", "[3, 4, 5]").as(List.class).get(1));
        }
    }

    @Test
    public void testAccessingHashes() {
        try (Context polyglot = Context.create()) {
            final Value access = polyglot.eval("ruby", "->(hash, key) { hash[key] }");
            final Value hash = polyglot.eval("ruby", "{'a' => 3, 'b' => 4, 'c' => 5}");
            assertEquals(4, access.execute(hash, "b").asInt());
        }
    }

    @Test
    public void testImplementInterface() {
        try (Context polyglot = Context.newBuilder().allowHostAccess(HostAccess.EXPLICIT).build()) {
            final FluidForce fluidForce = polyglot.eval("ruby", FluidForce.RUBY_SOURCE).as(FluidForce.class);
            assertEquals(5587.008375144088, fluidForce.getFluidForce(2.0, 3.0, 6.0), 0.01);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void testImplementLambda() {
        try (Context polyglot = Context.create()) {
            final BiFunction adder = polyglot.eval("ruby", "lambda { |a, b| a + b }").as(BiFunction.class);
            assertEquals(16, (int) adder.apply(14, 2));
        }
    }

    @Test
    public void testParseOnceRunMany() {
        try (Context polyglot = Context.create()) {
            final Value parsedOnce = polyglot.eval("ruby", "lambda { 14 }");
            assertEquals(14, parsedOnce.execute().asInt());
        }
    }

    @Test
    public void testLocalVariablesNotSharedBetweenNonInteractiveEval() {
        try (Context polyglot = Context.create()) {
            polyglot.eval("ruby", "a = 14");
            assertTrue(polyglot.eval("ruby", "defined?(a).nil?").asBoolean());
        }
    }

    @Test
    public void testLocalVariablesSharedBetweenInteractiveEval() {
        try (Context polyglot = Context.create()) {
            polyglot.eval(Source.newBuilder("ruby", "a = 14", "test").interactive(true).buildLiteral());
            assertFalse(polyglot.eval(Source.newBuilder("ruby", "defined?(a).nil?", "test").interactive(true).buildLiteral()).asBoolean());
            polyglot.eval(Source.newBuilder("ruby", "b = 2", "test").interactive(true).buildLiteral());
            assertEquals(16, polyglot.eval(Source.newBuilder("ruby", "a + b", "test").interactive(true).buildLiteral()).asInt());
        }
    }

    @Test
    public void testLocalVariablesSharedBetweenInteractiveEvalChangesParsing() {
        try (Context polyglot = Context.create()) {
            polyglot.eval(Source.newBuilder("ruby", "def foo; 12; end", "test").interactive(true).buildLiteral());
            assertEquals(12, polyglot.eval(Source.newBuilder("ruby", "foo", "test").interactive(true).buildLiteral()).asInt());
            polyglot.eval(Source.newBuilder("ruby", "foo = 42", "test").interactive(true).buildLiteral());
            assertEquals(42, polyglot.eval(Source.newBuilder("ruby", "foo", "test").interactive(true).buildLiteral()).asInt());
        }
    }

    @Test
    public void testLocalVariablesAreNotSharedBetweenInteractiveAndNonInteractive() {
        try (Context polyglot = Context.create()) {
            polyglot.eval(Source.newBuilder("ruby", "a = 14", "test").interactive(false).buildLiteral());
            polyglot.eval(Source.newBuilder("ruby", "b = 2", "test").interactive(true).buildLiteral());
            assertTrue(polyglot.eval(Source.newBuilder("ruby", "defined?(a).nil?", "test").interactive(true).buildLiteral()).asBoolean());
            assertTrue(polyglot.eval(Source.newBuilder("ruby", "defined?(b).nil?", "test").interactive(false).buildLiteral()).asBoolean());
            assertFalse(polyglot.eval(Source.newBuilder("ruby", "defined?(b).nil?", "test").interactive(true).buildLiteral()).asBoolean());
            assertTrue(polyglot.eval(Source.newBuilder("ruby", "TOPLEVEL_BINDING.eval('defined?(a).nil?')", "test").interactive(false).buildLiteral()).asBoolean());
            assertTrue(polyglot.eval(Source.newBuilder("ruby", "TOPLEVEL_BINDING.eval('defined?(b).nil?')", "test").interactive(false).buildLiteral()).asBoolean());
            assertTrue(polyglot.eval(Source.newBuilder("ruby", "TOPLEVEL_BINDING.eval('defined?(a).nil?')", "test").interactive(true).buildLiteral()).asBoolean());
            assertTrue(polyglot.eval(Source.newBuilder("ruby", "TOPLEVEL_BINDING.eval('defined?(b).nil?')", "test").interactive(true).buildLiteral()).asBoolean());
        }
    }

}
