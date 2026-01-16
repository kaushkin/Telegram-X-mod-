#include <vector>
#include <string>
#include <map>
#include <optional>
#include <memory>

#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_decoder.h"
#include "api/video_codecs/video_encoder.h"
#include "modules/video_coding/include/video_codec_interface.h"
// #include "modules/video_coding/svc/scalable_video_controller.h" // Might be needed for StreamLayersConfig
#include "media/base/codec.h"
// #include "api/environment/environment.h" // For Environment

namespace webrtc {

// Stubs for H264
bool H264IsSameProfile(const std::map<std::string, std::string>& params1,
                       const std::map<std::string, std::string>& params2) {
  return false;
}

std::optional<H264ProfileLevelId> ParseSdpForH264ProfileLevelId(
    const std::map<std::string, std::string>& params) {
  return std::nullopt;
}

std::string H264ProfileLevelIdToString(const H264ProfileLevelId& profile_level_id) {
  return "";
}

std::vector<SdpVideoFormat> SupportedH264DecoderCodecs() {
  return {};
}

std::unique_ptr<VideoDecoder> H264Decoder::Create() {
  return nullptr;
}


// Stubs for VP8
std::unique_ptr<VideoDecoder> VP8Decoder::Create() {
  return nullptr;
}

std::unique_ptr<VideoEncoder> VP8Encoder::Create() {
  return nullptr;
}

// Missing symbol: webrtc::CreateVp8Decoder(webrtc::Environment const&)
// We need to match the signature blindly if we can't accept Environment type.
// But likely it is defined in a header. Let's try to declare it.
class Environment;
std::unique_ptr<VideoDecoder> CreateVp8Decoder(const Environment& env) {
    return nullptr;
}


// Stubs for VP9
std::unique_ptr<VideoDecoder> VP9Decoder::Create() {
  return nullptr;
}

std::unique_ptr<VideoEncoder> VP9Encoder::Create(const cricket::VideoCodec& codec) {
  return nullptr;
}

// For VP9Encoder::Create() (no args? Linker said webrtc::VP9Encoder::Create())
std::unique_ptr<VideoEncoder> VP9Encoder::Create() {
    return nullptr;
}

std::vector<SdpVideoFormat> SupportedVP9DecoderCodecs() {
  return {};
}

std::vector<SdpVideoFormat> SupportedVP9Codecs(bool profile_id) {
    return {};
}

// VP9Encoder::SupportsScalabilityMode(webrtc::ScalabilityMode)
// ScalabilityMode is likely an enum.
// We can try to use int if we don't include the header, but C++ mangling checks types.
// We need the proper header. 
// "api/video_codecs/scalability_mode.h" ? 
// Let's assume it's in video_encoder.h or similar.
bool VP9Encoder::SupportsScalabilityMode(ScalabilityMode mode) {
    return false;
}

// Stubs for SVC Config
// webrtc::GetSvcConfig(unsigned long, unsigned long, float, unsigned long, unsigned long, unsigned long, bool, std::__ndk1::optional<webrtc::ScalableVideoController::StreamLayersConfig>)
// Use size_t for unsigned long? linker says unsigned long.
// StreamLayersConfig is nested in ScalableVideoController.
// We need headers for these.
// modules/video_coding/include/video_codec_interface.h might have it.

std::vector<SpatialLayer> GetSvcConfig(size_t width, size_t height, float fps,
                                       size_t first_active_layer, size_t num_layers,
                                       size_t max_total_bitrate, bool is_flexible,
                                       std::optional<ScalableVideoController::StreamLayersConfig> stream_layers_config) {
  return {};
}

// webrtc::GetVp9SvcConfig(webrtc::VideoCodec&)
std::vector<SpatialLayer> GetVp9SvcConfig(VideoCodec& codec) {
    return {};
}


} // namespace webrtc
