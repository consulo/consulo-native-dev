/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.nativeDev.impl.debugger;

import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.execution.debug.XDebugProcess;
import consulo.execution.debug.XDebugProcessStarter;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.RunContentDescriptor;
import consulo.logging.Logger;
import consulo.nativeDev.debugger.NativeDebugProcess;
import consulo.nativeDev.debugger.NativeDebugSessionStarter;
import consulo.nativeDev.debugger.NativeDebuggerInstallation;
import consulo.nativeDev.debugger.NativeDebuggerProvider;
import consulo.nativeDev.debugger.driver.NativeDebugTarget;
import consulo.nativeDev.localize.NativeDevLocalize;
import consulo.process.ExecutionException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
@Singleton
@ServiceImpl
public class NativeDebugSessionStarterImpl implements NativeDebugSessionStarter {
    private static final Logger LOG = Logger.getInstance(NativeDebugSessionStarterImpl.class);

    private final Application myApplication;

    @Inject
    public NativeDebugSessionStarterImpl(Application application) {
        myApplication = application;
    }

    @Override
    public RunContentDescriptor startSession(ExecutionEnvironment environment, NativeDebugTarget target, @Nullable String debuggerProviderId)
        throws ExecutionException {
        List<NativeDebuggerProvider> providers = new ArrayList<>();
        myApplication.getExtensionPoint(NativeDebuggerProvider.class).forEach(providers::add);
        providers.sort(Comparator.comparingInt(NativeDebuggerProvider::getPriority).reversed());

        NativeDebuggerProvider debuggerProvider = null;
        NativeDebuggerInstallation debuggerInstallation = null;
        if (debuggerProviderId == null) {
            for (NativeDebuggerInstallation installation : target.setup().getToolchainDebuggers()) {
                NativeDebuggerProvider candidate = findProvider(providers, installation.providerId());
                if (candidate != null && Files.isRegularFile(installation.executable())) {
                    debuggerProvider = candidate;
                    debuggerInstallation = installation;
                    break;
                }
            }
        }

        for (NativeDebuggerProvider candidate : providers) {
            if (debuggerProvider != null) {
                break;
            }
            if (debuggerProviderId != null && !debuggerProviderId.equals(candidate.getId())) {
                continue;
            }
            List<NativeDebuggerInstallation> installations = findInstallations(candidate);
            if (!installations.isEmpty()) {
                debuggerProvider = candidate;
                debuggerInstallation = installations.get(0);
                break;
            }
        }

        if (debuggerProvider == null || debuggerInstallation == null) {
            throw new ExecutionException(debuggerProviderId == null
                ? NativeDevLocalize.nativeDebuggerNotFound().get()
                : NativeDevLocalize.nativeDebuggerNotInstalled(debuggerProviderId).get());
        }

        NativeDebuggerProvider selectedProvider = debuggerProvider;
        NativeDebuggerInstallation selectedInstallation = debuggerInstallation;
        XDebugSession session = XDebuggerManager.getInstance(environment.getProject()).startSession(environment, new XDebugProcessStarter() {
            @Override
            public XDebugProcess start(XDebugSession session) throws ExecutionException {
                XDebugProcess process = selectedProvider.createProcess(session, target, selectedInstallation);
                if (!(process instanceof NativeDebugProcess nativeProcess)) {
                    throw new ExecutionException(selectedProvider.getId() + " did not create a native debug process");
                }
                nativeProcess.start();
                return process;
            }
        });
        return session.getRunContentDescriptor();
    }

    private static @Nullable NativeDebuggerProvider findProvider(List<NativeDebuggerProvider> providers, String providerId) {
        for (NativeDebuggerProvider provider : providers) {
            if (provider.getId().equals(providerId)) {
                return provider;
            }
        }
        return null;
    }

    private static List<NativeDebuggerInstallation> findInstallations(NativeDebuggerProvider provider) {
        try {
            return provider.findInstallations();
        }
        catch (RuntimeException e) {
            LOG.error("Native debugger provider failed: " + provider.getId(), e);
            return List.of();
        }
    }
}
