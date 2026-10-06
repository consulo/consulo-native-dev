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
package consulo.nativeDev.perf.internal;

import consulo.execution.profiler.configuration.ProfilerConfigurationStateBase;

/**
 * @author VISTALL
 * @since 2026-10-06
 */
public class PerfProfilerConfigurationState extends ProfilerConfigurationStateBase {
    public static final int MIN_SAMPLING_FREQUENCY = 1;
    public static final int MAX_SAMPLING_FREQUENCY = 100000;
    public static final int DEFAULT_SAMPLING_FREQUENCY = 1000;

    private String myPerfPath = "";
    private int mySamplingFrequency = DEFAULT_SAMPLING_FREQUENCY;
    private PerfCallGraphMode myCallGraphMode = PerfCallGraphMode.DWARF;
    private boolean myInlineFrames;
    private String myAdditionalArguments = "";

    @Override
    public String getConfigurationTypeId() {
        return PerfProfilerConfigurationType.ID;
    }

    public String getPerfPath() {
        return myPerfPath;
    }

    public void setPerfPath(String perfPath) {
        myPerfPath = perfPath;
    }

    public int getSamplingFrequency() {
        return mySamplingFrequency;
    }

    public void setSamplingFrequency(int samplingFrequency) {
        mySamplingFrequency = Math.clamp(samplingFrequency, MIN_SAMPLING_FREQUENCY, MAX_SAMPLING_FREQUENCY);
    }

    public PerfCallGraphMode getCallGraphMode() {
        return myCallGraphMode;
    }

    public void setCallGraphMode(PerfCallGraphMode callGraphMode) {
        myCallGraphMode = callGraphMode;
    }

    public boolean isInlineFrames() {
        return myInlineFrames;
    }

    public void setInlineFrames(boolean inlineFrames) {
        myInlineFrames = inlineFrames;
    }

    public String getAdditionalArguments() {
        return myAdditionalArguments;
    }

    public void setAdditionalArguments(String additionalArguments) {
        myAdditionalArguments = additionalArguments;
    }
}
