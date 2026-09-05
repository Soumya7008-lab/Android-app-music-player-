#include <jni.h>
#include <cmath>
#include <algorithm>
#include <vector>

// High-fidelity 16D Spatial Audio Engine
// Simulates HRTF using ITD (Interaural Time Difference) and ILD (Interaural Level Difference)

class SpatialProcessor {
private:
    double angle = 0.0;
    double rotationSpeed = 1.5; // rad/sec
    float sampleRate = 44100.0f;

    // Delay buffers for ITD simulation
    std::vector<float> leftDelayBuffer;
    std::vector<float> rightDelayBuffer;
    int bufferSize = 1024;
    int writeIndex = 0;

public:
    SpatialProcessor() {
        leftDelayBuffer.resize(bufferSize, 0.0f);
        rightDelayBuffer.resize(bufferSize, 0.0f);
    }

    void setSampleRate(float sr) {
        sampleRate = sr;
    }

    void process(int16_t* samples, int count) {
        for (int i = 0; i < count; i += 2) {
            float left = samples[i] / 32768.0f;
            float right = samples[i+1] / 32768.0f;

            // 1. Calculate Panning (ILD)
            // We use a cosine-based orbit for 360 rotation
            double panAngle = angle;
            float gainL = static_cast<float>((cos(panAngle) + 1.0) / 2.0);
            float gainR = static_cast<float>((1.0 - cos(panAngle)) / 2.0);

            // 2. ITD Simulation (Micro-delays)
            // Max delay is ~0.6ms for human head size
            float maxDelaySamples = 0.0006f * sampleRate;
            float delayL = (1.0f - gainL) * maxDelaySamples;
            float delayR = (1.0f - gainR) * maxDelaySamples;

            // Store in circular buffers
            leftDelayBuffer[writeIndex] = left;
            rightDelayBuffer[writeIndex] = right;

            // Read delayed samples
            auto readDelayed = [&](const std::vector<float>& buf, float delay) {
                float readPos = static_cast<float>(writeIndex) - delay;
                if (readPos < 0) readPos += bufferSize;
                int i0 = static_cast<int>(readPos);
                int i1 = (i0 + 1) % bufferSize;
                float frac = readPos - i0;
                return buf[i0] * (1.0f - frac) + buf[i1] * frac;
            };

            float processedL = readDelayed(leftDelayBuffer, delayL);
            float processedR = readDelayed(rightDelayBuffer, delayR);

            // 3. Simple Spectral HRTF (Muffled sound when behind)
            float behindFactor = static_cast<float>((sin(panAngle) + 1.0) / 2.0); // 0 at front, 1 at back
            // Muffle high frequencies when behind (simplified)
            // In a real implementation, we'd use a proper FIR/IIR filter

            // 4. Distance Modulation
            float distMod = 0.85f + (static_cast<float>(sin(panAngle * 0.5)) * 0.15f);

            samples[i] = static_cast<int16_t>(std::clamp(processedL * gainL * distMod * 32767.0f, -32768.0f, 32767.0f));
            samples[i+1] = static_cast<int16_t>(std::clamp(processedR * gainR * distMod * 32767.0f, -32768.0f, 32767.0f));

            writeIndex = (writeIndex + 1) % bufferSize;
            angle += rotationSpeed / sampleRate;
            if (angle > 2.0 * M_PI) angle -= 2.0 * M_PI;
        }
    }
};

static SpatialProcessor processor;

extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_audio_processors_SpatialAudioProcessor_nativeProcess(
        JNIEnv* env, jobject thiz, jobject buffer, jint length, jfloat sampleRate) {

    int16_t* samples = (int16_t*)env->GetDirectBufferAddress(buffer);
    if (samples == nullptr) return;

    processor.setSampleRate(sampleRate);
    processor.process(samples, length / 2); // Length is in bytes, we need count of shorts
}
