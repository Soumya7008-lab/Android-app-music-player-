#include <jni.h>
#include <cmath>
#include <algorithm>
#include <vector>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// --- STABLE STUDIO ENGINE (16-BIT DIRECT PATH) ---
const float TWO_PI = 6.2831853f;
const float INV_32768 = 1.0f / 32768.0f;

class Biquad {
public:
    float b0, b1, b2, a1, a2;
    float x1, x2, y1, y2;
    Biquad() : b0(1), b1(0), b2(0), a1(0), a2(0), x1(0), x2(0), y1(0), y2(0) {}

    void setPeaking(float freq, float sampleRate, float Q, float gainDb) {
        float A = powf(10.0f, gainDb / 40.0f);
        float w0 = TWO_PI * freq / sampleRate;
        float alpha = sinf(w0) / (2.0f * Q);
        float cosW0 = cosf(w0);
        float a0 = 1.0f + alpha / A;
        b0 = (1.0f + alpha * A) / a0;
        b1 = (-2.0f * cosW0) / a0;
        b2 = (1.0f - alpha * A) / a0;
        a1 = (-2.0f * cosW0) / a0;
        a2 = (1.0f - alpha / A) / a0;
    }

    inline float process(float x) {
        float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1; x1 = x;
        y2 = y1; y1 = y;
        return y;
    }
};

class StableAudioEngine {
private:
    double angle = 0.0;
    float sampleRate = 44100.0f;
    std::vector<float> leftDelayBuffer;
    std::vector<float> rightDelayBuffer;
    int bufferSize = 4096;
    int writeIndex = 0;
    Biquad filtersL[5];
    Biquad filtersR[5];
    float eqFrequencies[5] = {60.0f, 230.0f, 910.0f, 3600.0f, 14000.0f};
    float prevEnv = 0.0f;
    float rotationSpeed = 7.0f;
    float clarityLevel = 0.5f;
    float snappiness = 0.5f;
    float soundstageWidth = 1.0f;

public:
    StableAudioEngine() {
        leftDelayBuffer.assign(bufferSize, 0.0f);
        rightDelayBuffer.assign(bufferSize, 0.0f);
    }

    void setSampleRate(float sr) {
        if (sr > 0) sampleRate = sr;
    }

    void updateParams(float speed, float clarity, float snap, float width, const float* gains) {
        rotationSpeed = speed;
        clarityLevel = clarity;
        snappiness = snap;
        soundstageWidth = width;
        if (gains) {
            for (int i = 0; i < 5; i++) {
                filtersL[i].setPeaking(eqFrequencies[i], sampleRate, 1.0f, gains[i]);
                filtersR[i].setPeaking(eqFrequencies[i], sampleRate, 1.0f, gains[i]);
            }
        }
    }

    inline float readHermite(const std::vector<float>& buf, float delay) {
        float readPos = static_cast<float>(writeIndex) - delay;
        while (readPos < 0) readPos += static_cast<float>(bufferSize);
        int i = static_cast<int>(readPos);
        float f = readPos - static_cast<float>(i);
        int i0 = (i - 1 + bufferSize) % bufferSize;
        int i1 = i % bufferSize;
        int i2 = (i + 1) % bufferSize;
        int i3 = (i + 2) % bufferSize;
        float a = (3 * (buf[i1] - buf[i2]) - buf[i0] + buf[i3]) * 0.5f;
        float b = 2 * buf[i2] + buf[i0] - 5 * buf[i1] * 0.5f - buf[i3] * 0.5f;
        float c = (buf[i2] - buf[i0]) * 0.5f;
        return (((a * f) + b) * f + c) * f + buf[i1];
    }

    void process(int16_t* samples, int count, bool spatialEnabled, int channels) {
        float attackCoeff = 0.01f + (snappiness * 0.1f);
        float angleStep = rotationSpeed / sampleRate;
        float maxDelay = (1.5f / 1000.0f) * sampleRate;

        for (int i = 0; i < count; i += channels) {
            float l = samples[i] * INV_32768;
            float r = (channels > 1) ? samples[i+1] * INV_32768 : l;

            // 1. EQ
            for (int b = 0; b < 5; b++) {
                l = filtersL[b].process(l);
                if (channels > 1) r = filtersR[b].process(r);
            }

            // 2. Transient
            float mono = (fabsf(l) + fabsf(r)) * 0.5f;
            float env = (mono > prevEnv) ? prevEnv + attackCoeff * (mono - prevEnv) : prevEnv + 0.001f * (mono - prevEnv);
            prevEnv = env;
            float tGain = 1.0f + (mono - env) * snappiness * 2.0f;
            l *= tGain; r *= tGain;

            // 3. Spatial
            leftDelayBuffer[writeIndex] = l;
            rightDelayBuffer[writeIndex] = r;
            if (spatialEnabled && channels > 1) {
                float s = sinf(angle), c = cosf(angle);
                float gL = 0.5f - (s * 0.48f), gR = 0.5f + (s * 0.48f);
                l = readHermite(leftDelayBuffer, gR * maxDelay) * gL * (0.85f + c * 0.15f);
                r = readHermite(rightDelayBuffer, gL * maxDelay) * gR * (0.85f + c * 0.15f);
                angle += angleStep;
                if (angle > TWO_PI) angle -= TWO_PI;
            }
            writeIndex = (writeIndex + 1) % bufferSize;

            // 4. M/S Width
            if (channels > 1) {
                float mid = (l + r) * 0.5f, side = (l - r) * 0.5f * soundstageWidth;
                l = mid + side; r = mid - side;
            }

            // 5. Exciter & Limiter
            auto finalize = [&](float input) {
                float harmonics = input * input * (input > 0 ? 1.0f : -1.0f);
                float out = input + (harmonics * clarityLevel * 0.2f);
                return std::clamp(out * (1.5f - 0.5f * out * out), -1.0f, 1.0f);
            };

            samples[i] = static_cast<int16_t>(finalize(l) * 32767.0f);
            if (channels > 1) samples[i+1] = static_cast<int16_t>(finalize(r) * 32767.0f);
        }
    }
};

static StableAudioEngine engine;

extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_audio_processors_SpatialAudioProcessor_nativeProcess(
        JNIEnv* env, jobject thiz, jobject buffer, jint length, jfloat sampleRate,
        jfloat speed, jfloat clarity, jfloat snappiness, jfloat width, jfloatArray eqGains,
        jboolean spatialEnabled, jint channels) {

    int16_t* samples = (int16_t*)env->GetDirectBufferAddress(buffer);
    if (!samples) return;

    jfloat* gains = env->GetFloatArrayElements(eqGains, nullptr);
    engine.setSampleRate(sampleRate);
    engine.updateParams(speed, clarity, snappiness, width, gains);
    if (gains) env->ReleaseFloatArrayElements(eqGains, gains, JNI_ABORT);

    engine.process(samples, length / 2, (bool)spatialEnabled, (int)channels);
}
