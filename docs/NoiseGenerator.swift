//
//  NoiseGenerator.swift
//  Reference implementation of the white / pink / brown noise generators.
//
//  Port of the Kotlin implementation documented in NOISE_IMPLEMENTATION.md.
//  Self-contained: drop this file in, call NoiseEngine.shared.play(.brown).
//
//  Requires iOS 13+ (AVAudioSourceNode).
//
//  IMPORTANT — background playback:
//  Add "audio" to UIBackgroundModes in Info.plist, otherwise iOS suspends the app when the
//  user leaves it and playback stops. (Signing Capabilities > Background Modes > Audio.)
//
//      <key>UIBackgroundModes</key>
//      <array><string>audio</string></array>
//

import AVFoundation

// MARK: - Colours

public enum NoiseColour: CaseIterable {
    case white, pink, brown
}

// MARK: - Fast deterministic PRNG
//
// Swift's SystemRandomNumberGenerator is not appropriate on the audio render thread. This is a
// plain xorshift64*: allocation-free, and deterministic so the calibration below is reproducible.

public struct XorShift64 {
    private var state: UInt64

    public init(seed: UInt64) {
        // Any non-zero state works; mix the seed so small seeds still decorrelate immediately.
        self.state = seed &* 0x9E37_79B9_7F4A_7C15 | 1
    }

    /// Uniform in [-1, 1).
    public mutating func nextUniform() -> Double {
        state ^= state >> 12
        state ^= state << 25
        state ^= state >> 27
        let value = state &* 2_685_821_657_736_338_717
        // Top 53 bits -> [0, 1), then map to [-1, 1).
        return Double(value >> 11) * (1.0 / 9_007_199_254_740_992.0) * 2.0 - 1.0
    }
}

// MARK: - Biquad

public struct Biquad {
    private let b0, b1, b2, a1, a2: Double
    private var x1 = 0.0, x2 = 0.0, y1 = 0.0, y2 = 0.0

    public init(b0: Double, b1: Double, b2: Double, a1: Double, a2: Double) {
        self.b0 = b0; self.b1 = b1; self.b2 = b2; self.a1 = a1; self.a2 = a2
    }

    public mutating func process(_ x: Double) -> Double {
        let y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
}

public enum BiquadDesign {
    public static let butterworthQ = 0.707_106_781_186_547_6

    public static func highPass(_ fs: Double, _ f0: Double, q: Double = butterworthQ) -> Biquad {
        let w0 = 2 * .pi * f0 / fs, cw = cos(w0), alpha = sin(w0) / (2 * q)
        let a0 = 1 + alpha
        return Biquad(b0: ((1 + cw) / 2) / a0, b1: (-(1 + cw)) / a0, b2: ((1 + cw) / 2) / a0,
                      a1: (-2 * cw) / a0, a2: (1 - alpha) / a0)
    }

    public static func lowPass(_ fs: Double, _ f0: Double, q: Double = butterworthQ) -> Biquad {
        let w0 = 2 * .pi * f0 / fs, cw = cos(w0), alpha = sin(w0) / (2 * q)
        let a0 = 1 + alpha
        return Biquad(b0: ((1 - cw) / 2) / a0, b1: (1 - cw) / a0, b2: ((1 - cw) / 2) / a0,
                      a1: (-2 * cw) / a0, a2: (1 - alpha) / a0)
    }

    public static func highShelf(_ fs: Double, _ f0: Double, gainDb: Double,
                                 q: Double = butterworthQ) -> Biquad {
        let A = pow(10.0, gainDb / 40.0)
        let w0 = 2 * .pi * f0 / fs, cw = cos(w0)
        let alpha = sin(w0) / 2 * (((A + 1 / A) * (1 / q - 1) + 2)).squareRoot()
        let t = 2 * A.squareRoot() * alpha
        let a0 = (A + 1) - (A - 1) * cw + t
        return Biquad(b0: (A * ((A + 1) + (A - 1) * cw + t)) / a0,
                      b1: (-2 * A * ((A - 1) + (A + 1) * cw)) / a0,
                      b2: (A * ((A + 1) + (A - 1) * cw - t)) / a0,
                      a1: (2 * ((A - 1) - (A + 1) * cw)) / a0,
                      a2: ((A + 1) - (A - 1) * cw - t) / a0)
    }

    /// Only used by the offline verification helpers.
    public static func bandPass(_ fs: Double, _ f0: Double, q: Double) -> Biquad {
        let w0 = 2 * .pi * f0 / fs, cw = cos(w0), alpha = sin(w0) / (2 * q)
        let a0 = 1 + alpha
        return Biquad(b0: alpha / a0, b1: 0, b2: -alpha / a0,
                      a1: (-2 * cw) / a0, a2: (1 - alpha) / a0)
    }
}

// MARK: - Colour filters

/// Paul Kellett's pink approximation: about -3 dB per octave.
public struct PinkFilter {
    private var b0 = 0.0, b1 = 0.0, b2 = 0.0, b3 = 0.0, b4 = 0.0, b5 = 0.0, b6 = 0.0
    public init() {}

    public mutating func process(_ w: Double) -> Double {
        b0 = 0.99886 * b0 + w * 0.0555179
        b1 = 0.99332 * b1 + w * 0.0750759
        b2 = 0.96900 * b2 + w * 0.1538520
        b3 = 0.86650 * b3 + w * 0.3104856
        b4 = 0.55000 * b4 + w * 0.5329522
        b5 = -0.7616 * b5 - w * 0.0168980
        let pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
        b6 = w * 0.115926
        return pink * 0.11
    }
}

/// Leaky integrator: about -6 dB per octave above its corner.
public struct BrownFilter {
    private let pole: Double
    private let drive: Double
    private var state = 0.0

    public init(pole: Double) {
        self.pole = pole
        self.drive = 1 - pole
    }

    public mutating func process(_ w: Double) -> Double {
        state = pole * state + drive * w
        return state
    }
}

// MARK: - Tuning

public enum NoiseTuning {
    /// 48 kHz is native on iOS hardware and is the rate the K-weighting coefficients are defined at.
    public static let sampleRate = 48_000.0

    public static let highPassHz = 30.0
    public static let lowPassHz = 18_000.0

    public static let brownCornerHz = 18.0
    public static var brownPole: Double { exp(-2 * .pi * brownCornerHz / sampleRate) }

    // Voicing — taste settings, not definitions of the colours. Every one of these exists because
    // the mathematically correct version was judged too bright. Raise the shelf gain (towards -6)
    // for brighter, lower it for darker.
    public static let whiteShelfHz = 2_000.0
    public static let whiteShelfDb = -12.0
    public static let whiteTopHz = 8_000.0

    public static let pinkShelfHz = 1_200.0
    public static let pinkShelfDb = -18.0
    public static let pinkTopHz = 9_000.0

    public static let brownTopHz = 1_600.0   // applied twice

    /// Headroom the calibration guarantees, so the limiter only catches occasional transients.
    public static let peakCeiling = 0.80
}

// MARK: - One channel of shaped noise

public struct NoiseChannel {
    private let colour: NoiseColour
    private var rng: XorShift64
    private var pink = PinkFilter()
    private var brown: BrownFilter
    private var highPass: Biquad
    private var lowPass: Biquad
    private var voicing: [Biquad]
    private let gain: Double

    public init(colour: NoiseColour,
                seed: UInt64,
                sampleRate: Double = NoiseTuning.sampleRate,
                gain: Double = 1.0,
                voiced: Bool = true) {
        self.colour = colour
        self.rng = XorShift64(seed: seed)
        self.brown = BrownFilter(pole: exp(-2 * .pi * NoiseTuning.brownCornerHz / sampleRate))
        self.highPass = BiquadDesign.highPass(sampleRate, NoiseTuning.highPassHz)
        self.lowPass = BiquadDesign.lowPass(sampleRate, NoiseTuning.lowPassHz)
        self.gain = gain

        guard voiced else { self.voicing = []; return }
        switch colour {
        case .white:
            // A shelf rather than a cut alone: a steep cut leaves whatever survives above the
            // corner audible as a separate hissy layer, where a shelf tilts it into the body.
            voicing = [BiquadDesign.highShelf(sampleRate, NoiseTuning.whiteShelfHz,
                                              gainDb: NoiseTuning.whiteShelfDb),
                       BiquadDesign.lowPass(sampleRate, NoiseTuning.whiteTopHz)]
        case .pink:
            voicing = [BiquadDesign.highShelf(sampleRate, NoiseTuning.pinkShelfHz,
                                              gainDb: NoiseTuning.pinkShelfDb),
                       BiquadDesign.lowPass(sampleRate, NoiseTuning.pinkTopHz)]
        case .brown:
            voicing = [BiquadDesign.lowPass(sampleRate, NoiseTuning.brownTopHz),
                       BiquadDesign.lowPass(sampleRate, NoiseTuning.brownTopHz)]
        }
    }

    public mutating func next() -> Double {
        let white = rng.nextUniform()
        var s: Double
        switch colour {
        case .white: s = white
        case .pink:  s = pink.process(white)
        case .brown: s = brown.process(white)
        }
        s = highPass.process(s)
        s = lowPass.process(s)
        for i in voicing.indices { s = voicing[i].process(s) }
        return s * gain
    }
}

// MARK: - Peak limiter (stereo-linked)

public struct PeakLimiter {
    private let threshold: Double
    private let releaseCoeff: Double
    private var gain = 1.0

    public init(threshold: Double = 0.97,
                sampleRate: Double = NoiseTuning.sampleRate,
                releaseSeconds: Double = 0.25) {
        self.threshold = threshold
        self.releaseCoeff = exp(-1.0 / (releaseSeconds * sampleRate))
    }

    /// Gain to apply to *every* channel of this frame, from the larger channel peak, so gain
    /// reduction never shifts the stereo image.
    public mutating func gainFor(_ peak: Double) -> Double {
        let needed = peak > threshold ? threshold / peak : 1.0
        gain = needed < gain ? needed : needed + (gain - needed) * releaseCoeff
        return gain
    }
}

// MARK: - Loudness (ITU-R BS.1770 K-weighting)

public enum Loudness {
    // Published coefficients, defined at 48 kHz.
    private static func stage1() -> Biquad {
        Biquad(b0: 1.535_124_859_586_97, b1: -2.691_696_189_406_38, b2: 1.198_392_810_852_85,
               a1: -1.690_659_293_182_41, a2: 0.732_480_774_215_85)
    }

    private static func stage2() -> Biquad {
        Biquad(b0: 1.0, b1: -2.0, b2: 1.0,
               a1: -1.990_047_454_833_98, a2: 0.990_072_250_366_21)
    }

    public static func kWeightedRms(_ count: Int, warmUp: Int = 4_800,
                                    _ next: () -> Double) -> Double {
        var s1 = stage1(), s2 = stage2()
        for _ in 0..<warmUp { _ = s2.process(s1.process(next())) }
        var sum = 0.0
        for _ in 0..<count {
            let y = s2.process(s1.process(next()))
            sum += y * y
        }
        return (sum / Double(count)).squareRoot()
    }

    public static func peak(_ count: Int, _ next: () -> Double) -> Double {
        var p = 0.0
        for _ in 0..<count { p = max(p, abs(next())) }
        return p
    }

    /// Energy in a narrow band — for verifying spectral slope offline.
    public static func bandEnergy(_ centreHz: Double, _ count: Int,
                                  sampleRate: Double = NoiseTuning.sampleRate,
                                  _ next: () -> Double) -> Double {
        var bp = BiquadDesign.bandPass(sampleRate, centreHz, q: 4.0)
        for _ in 0..<4_800 { _ = bp.process(next()) }
        var sum = 0.0
        for _ in 0..<count {
            let y = bp.process(next())
            sum += y * y
        }
        return (sum / Double(count)).squareRoot()
    }
}

// MARK: - Calibration
//
// Colours are matched by K-weighted loudness, not RMS: brown's energy sits where the ear is least
// sensitive, so equal RMS leaves it sounding much quieter. Gains are derived from the real chain at
// startup (a few ms) so they can never drift out of step with the filters.

public enum NoiseCalibration {
    private static let measureSamples = 24_000

    private struct Unity { let loudness: Double; let peak: Double }

    private static let unity: [NoiseColour: Unity] = {
        var result = [NoiseColour: Unity]()
        for colour in NoiseColour.allCases {
            var loudChannel = NoiseChannel(colour: colour, seed: UInt64(abs(colour.hashValue)) &+ 1,
                                           gain: 1.0)
            let loudness = Loudness.kWeightedRms(measureSamples) { loudChannel.next() }
            var peakChannel = NoiseChannel(colour: colour, seed: UInt64(abs(colour.hashValue)) &+ 2,
                                           gain: 1.0)
            let peak = Loudness.peak(measureSamples) { peakChannel.next() }
            result[colour] = Unity(loudness: loudness, peak: peak)
        }
        return result
    }()

    /// The loudest shared level at which every colour still peaks below the ceiling.
    public static let targetLoudness: Double = unity.values
        .map { NoiseTuning.peakCeiling * $0.loudness / $0.peak }
        .min() ?? 0.1

    public static func gain(for colour: NoiseColour) -> Double {
        guard let u = unity[colour] else { return 1.0 }
        return targetLoudness / u.loudness
    }

    /// Playback-ready, calibrated channel.
    public static func channel(_ colour: NoiseColour, seed: UInt64,
                               sampleRate: Double = NoiseTuning.sampleRate) -> NoiseChannel {
        NoiseChannel(colour: colour, seed: seed, sampleRate: sampleRate, gain: gain(for: colour))
    }
}

// MARK: - Render state
//
// A reference type so the render block can mutate it without capturing `self` or allocating.

private final class RenderState {
    var left: NoiseChannel
    var right: NoiseChannel
    var limiter: PeakLimiter
    var volume: Double = 0.7

    init(colour: NoiseColour, sampleRate: Double) {
        // Independent seeds -> decorrelated channels. This is what makes the noise sound wide and
        // enveloping; two copies of the same stream collapse to a point inside the head.
        left = NoiseCalibration.channel(colour, seed: 0x2545_F491_4F6C_DD1D, sampleRate: sampleRate)
        right = NoiseCalibration.channel(colour, seed: 0x9E37_79B9_7F4A_7C15, sampleRate: sampleRate)
        limiter = PeakLimiter(sampleRate: sampleRate)
    }
}

// MARK: - Engine

public final class NoiseEngine {

    public static let shared = NoiseEngine()

    private let engine = AVAudioEngine()
    private var sourceNode: AVAudioSourceNode?
    private var state: RenderState?
    private(set) public var currentColour: NoiseColour?

    private init() {
        NotificationCenter.default.addObserver(
            self, selector: #selector(handleInterruption(_:)),
            name: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance())
    }

    /// Volume 0...1, applied before the limiter. Safe to call while playing.
    public var volume: Double {
        get { state?.volume ?? 0.7 }
        set { state?.volume = min(max(newValue, 0), 1) }
    }

    public func play(_ colour: NoiseColour) {
        stop()
        configureSession()

        let sampleRate = AVAudioSession.sharedInstance().sampleRate
        let renderState = RenderState(colour: colour, sampleRate: sampleRate)
        self.state = renderState

        // Non-interleaved float32 stereo: one buffer per channel.
        guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 2)
        else { return }

        let node = AVAudioSourceNode(format: format) { _, _, frameCount, audioBufferList -> OSStatus in
            let buffers = UnsafeMutableAudioBufferListPointer(audioBufferList)
            let volume = renderState.volume
            for frame in 0..<Int(frameCount) {
                let l = renderState.left.next() * volume
                let r = renderState.right.next() * volume
                let reduction = renderState.limiter.gainFor(max(abs(l), abs(r)))
                for (channel, buffer) in buffers.enumerated() {
                    guard let data = buffer.mData?.assumingMemoryBound(to: Float.self) else { continue }
                    data[frame] = Float((channel == 0 ? l : r) * reduction)
                }
            }
            return noErr
        }

        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: format)
        sourceNode = node

        do {
            try engine.start()
            currentColour = colour
        } catch {
            stop()
        }
    }

    public func stop() {
        engine.stop()
        if let node = sourceNode {
            engine.detach(node)
            sourceNode = nil
        }
        state = nil
        currentColour = nil
    }

    private func configureSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            // .mixWithOthers is the iOS equivalent of declining to pause on audio-focus loss:
            // ambient noise keeps playing underneath other apps instead of being interrupted by
            // them. Phone calls still interrupt (handled below), which is what you want.
            try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
            // The K-weighting coefficients above are defined at 48 kHz; ask for that rate.
            try session.setPreferredSampleRate(NoiseTuning.sampleRate)
            try session.setActive(true)
        } catch {
            // Fall through: the engine will still try to start with whatever the session allows.
        }
    }

    /// Phone calls and other hard interruptions.
    @objc private func handleInterruption(_ note: Notification) {
        guard let info = note.userInfo,
              let raw = info[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }

        switch type {
        case .began:
            engine.pause()
        case .ended:
            let options = (info[AVAudioSessionInterruptionOptionKey] as? UInt).map {
                AVAudioSession.InterruptionOptions(rawValue: $0)
            }
            if options?.contains(.shouldResume) == true {
                try? AVAudioSession.sharedInstance().setActive(true)
                try? engine.start()
            }
        @unknown default:
            break
        }
    }
}
