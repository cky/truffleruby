/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.processor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import org.truffleruby.PopulateBuildInformation;

@SupportedAnnotationTypes("org.truffleruby.PopulateBuildInformation")
public class BuildInformationProcessor extends AbstractProcessor {

    private static final String SUFFIX = "Impl";

    private final Set<String> processed = new HashSet<>();

    private File trufflerubyHome;
    private String rubyVersion;
    private String rubyBaseVersion;
    private int rubyRevision;
    private String revision;
    private String compileDate;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        try {
            trufflerubyHome = findHome();
            rubyVersion = runCommand("tool/query-versions-json.rb ruby.version");
            rubyBaseVersion = runCommand("tool/query-versions-json.rb ruby.base");
            rubyRevision = Integer.parseInt(runCommand("tool/query-versions-json.rb ruby.revision"));
            revision = runCommand("git rev-parse --short=8 HEAD");
            compileDate = runCommand("git log -1 --date=short --pretty=format:%cd");
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    private File findHome() throws URISyntaxException {
        CodeSource codeSource = getClass().getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            throw new RuntimeException("Could not find the source code for " + getClass());
        }
        File source = new File(codeSource.getLocation().toURI());
        // this is probably `mxbuild/org.truffleruby.processor/bin` or `mxbuild/dists/jdk1.8/truffleruby-processor.jar`
        // let's try to find `mxbuild`
        while (!source.getName().equals("mxbuild")) {
            source = source.getParentFile();
            if (source == null) {
                throw new RuntimeException("Could not find `mxbuild` in the source path for " + getClass() + ": " + codeSource.getLocation());
            }
        }
        return source.getParentFile();
    }

    private String runCommand(String command) throws IOException, InterruptedException {
        final Process git = new ProcessBuilder(command.split("\\s+"))
                        .directory(trufflerubyHome)
                        .start();
        final String firstLine;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(git.getInputStream()))) {
            firstLine = reader.readLine();
        }

        final int exitCode = git.waitFor();
        if (exitCode != 0) {
            throw new Error("Command " + command + " failed with exit code " + exitCode);
        }
        return firstLine;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        assert isInitialized();
        if (!annotations.isEmpty()) {
            for (Element element : roundEnvironment.getElementsAnnotatedWith(PopulateBuildInformation.class)) {
                try {
                    processBuildInformation((TypeElement) element);
                } catch (Exception e) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, e.getClass() + " " + e.getMessage(), element);
                }
            }
        }

        return true;
    }

    private void processBuildInformation(TypeElement element) throws Exception {
        final PackageElement packageElement = (PackageElement) element.getEnclosingElement();
        final String packageName = packageElement.getQualifiedName().toString();

        final String qualifiedName = element.getQualifiedName().toString();

        if (!processed.add(qualifiedName)) {
            // Already processed, do nothing. This seems an Eclipse bug.
            return;
        }

        final JavaFileObject output = processingEnv.getFiler().createSourceFile(qualifiedName + SUFFIX, element);

        try (PrintStream stream = new PrintStream(output.openOutputStream(), true, "UTF-8")) {
            stream.println("/*\n" +
                            " * Copyright (c) " + Calendar.getInstance().get(Calendar.YEAR) + " Oracle and/or its affiliates. All rights reserved. This\n" +
                            " * code is released under a tri EPL/GPL/LGPL license. You can use it,\n" +
                            " * redistribute it and/or modify it under the terms of the:\n" +
                            " *\n" +
                            " * Eclipse Public License version 1.0, or\n" +
                            " * GNU General Public License version 2, or\n" +
                            " * GNU Lesser General Public License version 2.1.\n" +
                            " */");
            stream.println("package " + packageName + ";");
            stream.println();
            stream.println("// This file is automatically generated from versions.json");
            stream.println();
            stream.println("import javax.annotation.Generated;");
            stream.println();
            stream.println("@Generated(\"" + getClass().getName() + "\")");
            stream.println("public class " + element.getSimpleName() + SUFFIX + " implements " + element.getSimpleName() + " {");
            stream.println();
            stream.println("    public static final " + element.getSimpleName() + " INSTANCE = new " + element.getSimpleName() + SUFFIX + "();");
            stream.println();
            stream.println("    // This backdoor constant is just for @TruffleLanguage.Registration, which needs a constant");
            stream.println("    public static final String RUBY_VERSION = \"" + rubyVersion + "\";");
            stream.println();

            for (Element e : element.getEnclosedElements()) {
                if (e instanceof ExecutableElement) {
                    final String name = e.getSimpleName().toString();

                    if (name.equals("getRubyRevision")) {
                        stream.println("    @Override");
                        stream.println("    public int " + name + "() {");
                        stream.println("        return " + rubyRevision + ";");
                        stream.println("    }");
                    } else {
                        final String value;

                        switch (name) {
                            case "getRubyVersion":
                                value = rubyVersion;
                                break;
                            case "getRubyBaseVersion":
                                value = rubyBaseVersion;
                                break;
                            case "getRevision":
                                value = revision;
                                break;
                            case "getCompileDate":
                                value = compileDate;
                                break;
                            default:
                                throw new UnsupportedOperationException(name + " method not understood");
                        }

                        stream.println("    @Override");
                        stream.println("    public String " + name + "() {");
                        stream.println("        return \"" + value + "\";");
                        stream.println("    }");
                        stream.println();
                    }
                }
            }

            stream.println("}");
        }
    }
}
