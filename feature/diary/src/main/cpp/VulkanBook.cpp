#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <android/trace.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <cstring>
#include <cstdio>
#include <fstream>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

namespace {
constexpr uint32_t MaxDraws=512, UniformBytes=176, CommandBytes=192, FramesInFlight=2;
struct Trace {
    explicit Trace(const char* name) { ATrace_beginSection(name); }
    ~Trace() { ATrace_endSection(); }
};
void check(VkResult r,const char* call) {
    if(r!=VK_SUCCESS) throw std::runtime_error(std::string(call)+": "+std::to_string(r));
}
#define VK_CHECK(call) check((call),#call)
struct Buffer { VkBuffer buffer{}; VkDeviceMemory memory{}; void* mapped{}; VkDeviceSize capacity{}; };
struct Image { VkImage image{}; VkDeviceMemory memory{}; VkImageView view{}; VkDeviceSize bytes{}; bool lazy{}; };
struct Mesh { Buffer vertices,indices; uint32_t count{}; };
struct Texture { Image image; VkDescriptorSet descriptor{}; };
struct Upload { Buffer staging; VkCommandBuffer command{}; VkFence fence{}; };
struct Frame { VkCommandBuffer command{}; VkFence fence{}; VkSemaphore acquired{}; uint64_t serial{}; };
struct RetiredTexture { Texture texture; uint64_t after{}; };
struct Command { float uniform[44]; int32_t mesh,texture,flags,reserved; };
static_assert(sizeof(Command)==CommandBytes);

// Re-entry creates a device, not new shader programs. Keep driver-owned cache
// bytes across loading/book contexts, never Vulkan handles or user files.
constexpr size_t MaxPipelineCacheBytes=4u*1024u*1024u;
std::mutex pipelineStoreMutex;
std::vector<uint8_t> pipelineStore;
std::string pipelineStoreKey;
std::string cacheKey(const VkPhysicalDeviceProperties& p) {
    return std::to_string(p.vendorID)+":"+std::to_string(p.deviceID)+":"+
        std::to_string(p.driverVersion)+":"+std::string(reinterpret_cast<const char*>(p.pipelineCacheUUID),VK_UUID_SIZE);
}

class Engine {
    ANativeWindow* window{};
    VkInstance instance{};
    VkSurfaceKHR surface{};
    VkPhysicalDevice physical{};
    VkDevice device{};
    uint32_t queueFamily{};
    VkQueue queue{};
    VkCommandPool commandPool{};
    std::array<Frame,FramesInFlight> frames{};
    uint32_t frameSlot{};
    uint64_t submittedSerial{},completedSerial{};
    VkDescriptorSetLayout descriptorLayout{};
    VkDescriptorPool descriptorPool{};
    VkPipelineLayout pipelineLayout{};
    VkPipelineCache pipelineCache{};
    std::string pipelineKey;
    std::string cacheFile;
    VkSampler sampler{};
    VkShaderModule vertex{},fragment{};
    Buffer uniform;
    uint32_t uniformStride{};
    VkSwapchainKHR swapchain{};
    VkFormat colorFormat{},depthFormat{};
    VkExtent2D extent{};
    VkExtent2D requestedExtent{};
    VkSampleCountFlagBits samples{VK_SAMPLE_COUNT_1_BIT};
    VkRenderPass renderPass{};
    Image depth,msaa;
    std::vector<VkImageView> views;
    std::vector<VkFramebuffer> framebuffers;
    // Present-wait semaphores belong to swapchain images, not CPU frame slots.
    std::vector<VkSemaphore> presented;
    std::unordered_map<int,VkPipeline> pipelines;
    std::unordered_map<int,Mesh> meshes;
    std::unordered_map<int,Texture> textures;
    std::vector<Upload> uploads;
    std::vector<Upload> idleUploads;
    static constexpr VkDeviceSize UploadPoolLimit=8u*1024u*1024u;
    VkDeviceSize idleUploadBytes{};
    uint64_t textureUploads{},stagingAllocations{},stagingReuses{},peakUploads{};
    // Resources evicted while recording cannot be destroyed until a later
    // frame fence covers both previous draws and any pending texture transfer.
    std::vector<Texture> retiredTextures;
    std::vector<RetiredTexture> inFlightRetiredTextures;
    int nextId=1;
    bool dirty=false;

    uint32_t memoryType(uint32_t bits,VkMemoryPropertyFlags flags) {
        VkPhysicalDeviceMemoryProperties p;vkGetPhysicalDeviceMemoryProperties(physical,&p);
        for(uint32_t i=0;i<p.memoryTypeCount;i++)
            if((bits&(1u<<i)) && (p.memoryTypes[i].propertyFlags&flags)==flags) return i;
        throw std::runtime_error("No compatible Vulkan memory type");
    }
    Buffer buffer(VkDeviceSize size,VkBufferUsageFlags usage,bool host) {
        Buffer b;b.capacity=size;
        VkBufferCreateInfo info{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};info.size=size;info.usage=usage;info.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
        VK_CHECK(vkCreateBuffer(device,&info,nullptr,&b.buffer));
        try {
            VkMemoryRequirements req;vkGetBufferMemoryRequirements(device,b.buffer,&req);
            VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};allocation.allocationSize=req.size;
            allocation.memoryTypeIndex=memoryType(req.memoryTypeBits,host?VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT:VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            VK_CHECK(vkAllocateMemory(device,&allocation,nullptr,&b.memory));
            VK_CHECK(vkBindBufferMemory(device,b.buffer,b.memory,0));
            if(host) VK_CHECK(vkMapMemory(device,b.memory,0,size,0,&b.mapped));
        } catch(...) { free(b);throw; }
        return b;
    }
    void free(Buffer& b) {
        if(b.mapped) vkUnmapMemory(device,b.memory);
        if(b.buffer) vkDestroyBuffer(device,b.buffer,nullptr);
        if(b.memory) vkFreeMemory(device,b.memory,nullptr);
        b={};
    }
    Image image(uint32_t w,uint32_t h,VkFormat format,VkImageUsageFlags usage,VkImageAspectFlags aspect,VkSampleCountFlagBits count=VK_SAMPLE_COUNT_1_BIT) {
        Image im;
        VkImageCreateInfo info{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};info.imageType=VK_IMAGE_TYPE_2D;info.format=format;
        info.extent={w,h,1};info.mipLevels=1;info.arrayLayers=1;info.samples=count;info.tiling=VK_IMAGE_TILING_OPTIMAL;
        info.usage=usage;info.sharingMode=VK_SHARING_MODE_EXCLUSIVE;info.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
        VK_CHECK(vkCreateImage(device,&info,nullptr,&im.image));
        try {
            VkMemoryRequirements req;vkGetImageMemoryRequirements(device,im.image,&req);
            VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};allocation.allocationSize=req.size;
            auto flags=VkMemoryPropertyFlags(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            // Tile GPUs may keep pass-local attachments entirely on chip. Desktop and
            // software devices without a lazy heap retain the same device-local fallback.
            if(usage&VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT) {
                VkPhysicalDeviceMemoryProperties properties;vkGetPhysicalDeviceMemoryProperties(physical,&properties);
                for(uint32_t i=0;i<properties.memoryTypeCount;i++) {
                    const auto lazy=flags|VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT;
                    if((req.memoryTypeBits&(1u<<i)) && (properties.memoryTypes[i].propertyFlags&lazy)==lazy) { flags=lazy;break; }
                }
            }
            allocation.memoryTypeIndex=memoryType(req.memoryTypeBits,flags);
            im.bytes=req.size;im.lazy=bool(flags&VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT);
            VK_CHECK(vkAllocateMemory(device,&allocation,nullptr,&im.memory));
            VK_CHECK(vkBindImageMemory(device,im.image,im.memory,0));
            im.view=imageView(im.image,format,aspect);
        } catch(...) { free(im);throw; }
        return im;
    }
    VkImageView imageView(VkImage im,VkFormat format,VkImageAspectFlags aspect) {
        VkImageViewCreateInfo info{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};info.image=im;info.viewType=VK_IMAGE_VIEW_TYPE_2D;info.format=format;
        info.subresourceRange={aspect,0,1,0,1};VkImageView view{};VK_CHECK(vkCreateImageView(device,&info,nullptr,&view));return view;
    }
    void free(Image& im) {
        if(im.view) vkDestroyImageView(device,im.view,nullptr);
        if(im.image) vkDestroyImage(device,im.image,nullptr);
        if(im.memory) vkFreeMemory(device,im.memory,nullptr);
        im={};
    }
    void collectUploads() {
        for(auto it=uploads.begin();it!=uploads.end();) {
            const auto result=vkGetFenceStatus(device,it->fence);
            if(result==VK_NOT_READY) {++it;continue;}
            check(result,"vkGetFenceStatus(upload)");
            // Retain the useful large slots instead of filling the pool with
            // one-off tiny shadow uploads. All these fences have completed.
            if(it->staging.capacity<=UploadPoolLimit) {
                while(it->staging.capacity+idleUploadBytes>UploadPoolLimit && !idleUploads.empty()) {
                    idleUploadBytes-=idleUploads.front().staging.capacity;
                    freeUpload(idleUploads.front());idleUploads.erase(idleUploads.begin());
                }
                idleUploadBytes+=it->staging.capacity;idleUploads.push_back(*it);
            } else freeUpload(*it);
            it=uploads.erase(it);
        }
    }
    void freeUpload(Upload& item) {
        if(item.command) vkFreeCommandBuffers(device,commandPool,1,&item.command);
        if(item.fence) vkDestroyFence(device,item.fence,nullptr);
        free(item.staging);item={};
    }
    void freeTexture(Texture& texture) {
        if(texture.descriptor) vkFreeDescriptorSets(device,descriptorPool,1,&texture.descriptor);
        free(texture.image);texture={};
    }
    void collectRetiredTextures() {
        for(auto it=inFlightRetiredTextures.begin();it!=inFlightRetiredTextures.end();) {
            if(it->after>completedSerial) {++it;continue;}
            freeTexture(it->texture);it=inFlightRetiredTextures.erase(it);
        }
    }
    VkShaderModule shader(AAssetManager* assets,const char* path) {
        AAsset* asset=AAssetManager_open(assets,path,AASSET_MODE_BUFFER);
        if(!asset) throw std::runtime_error(std::string("Missing SPIR-V asset: ")+path);
        const auto bytes=AAsset_getLength(asset);std::vector<uint32_t> code((bytes+3)/4);
        const auto read=AAsset_read(asset,code.data(),bytes);AAsset_close(asset);
        if(bytes%4 || read!=bytes) throw std::runtime_error("Invalid SPIR-V asset");
        VkShaderModuleCreateInfo info{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};info.codeSize=bytes;info.pCode=code.data();
        VkShaderModule module{};VK_CHECK(vkCreateShaderModule(device,&info,nullptr,&module));return module;
    }
    void destroyPipelines() {
        for(auto [_,p]:pipelines) vkDestroyPipeline(device,p,nullptr);pipelines.clear();
    }
    void destroySwapchain() {
        for(auto f:framebuffers) vkDestroyFramebuffer(device,f,nullptr);framebuffers.clear();
        for(auto s:presented) vkDestroySemaphore(device,s,nullptr);presented.clear();
        if(renderPass) vkDestroyRenderPass(device,renderPass,nullptr);renderPass={};
        free(depth);free(msaa);
        for(auto v:views) vkDestroyImageView(device,v,nullptr);views.clear();
        if(swapchain) vkDestroySwapchainKHR(device,swapchain,nullptr);swapchain={};
    }
    void createSwapchain() {
        VK_CHECK(vkDeviceWaitIdle(device));completedSerial=submittedSerial;
        collectUploads();collectRetiredTextures();destroySwapchain();
        VkSurfaceCapabilitiesKHR caps;VK_CHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical,surface,&caps));
        uint32_t count=0;VK_CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical,surface,&count,nullptr));
        if(!count) throw std::runtime_error("No Vulkan surface formats");
        std::vector<VkSurfaceFormatKHR> formats(count);VK_CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical,surface,&count,formats.data()));
        auto format=formats.front();
        for(auto f:formats) if((f.format==VK_FORMAT_R8G8B8A8_UNORM||f.format==VK_FORMAT_B8G8R8A8_UNORM) && f.colorSpace==VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {format=f;break;}
        if(format.format==VK_FORMAT_UNDEFINED) format.format=VK_FORMAT_R8G8B8A8_UNORM;
        // Extent/rotation alone does not change render-pass compatibility. Retain
        // pipelines with dynamic viewport/scissor instead of recompiling nine variants.
        if(colorFormat!=format.format) destroyPipelines();
        colorFormat=format.format;
        // Android WSI permits imageExtent anywhere in min/maxImageExtent, even
        // when currentExtent differs (presentation scales to the consumer). A
        // TextureView's preferred size can lag its UI callback by one buffer, so
        // use the same explicit pixel dimensions as Kotlin's projection instead.
        extent={std::clamp(requestedExtent.width,caps.minImageExtent.width,caps.maxImageExtent.width),
                std::clamp(requestedExtent.height,caps.minImageExtent.height,caps.maxImageExtent.height)};
        if(!extent.width||!extent.height) throw std::runtime_error("Empty Vulkan surface");
        VkCompositeAlphaFlagBitsKHR alpha=VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        for(auto candidate:{VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR})
            if(caps.supportedCompositeAlpha&candidate) {alpha=candidate;break;}
        if(alpha==VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) throw std::runtime_error("Vulkan surface cannot composite transparent book");
        VkSwapchainCreateInfoKHR info{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};info.surface=surface;
        // Two CPU frame slots, one additional present image: the compositor can
        // retain the previous image without stalling the next touch-driven draw.
        info.minImageCount=std::max(3u,caps.minImageCount);if(caps.maxImageCount) info.minImageCount=std::min(info.minImageCount,caps.maxImageCount);
        info.imageFormat=colorFormat;info.imageColorSpace=format.colorSpace;info.imageExtent=extent;info.imageArrayLayers=1;
        info.imageUsage=VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;info.imageSharingMode=VK_SHARING_MODE_EXCLUSIVE;
        // TextureView uses a local surface. Identity preserves its existing Compose rotation.
        info.preTransform=(caps.supportedTransforms&VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)?VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR:caps.currentTransform;
        info.compositeAlpha=alpha;info.presentMode=VK_PRESENT_MODE_FIFO_KHR;info.clipped=VK_TRUE;
        VK_CHECK(vkCreateSwapchainKHR(device,&info,nullptr,&swapchain));
        VK_CHECK(vkGetSwapchainImagesKHR(device,swapchain,&count,nullptr));std::vector<VkImage> images(count);
        VK_CHECK(vkGetSwapchainImagesKHR(device,swapchain,&count,images.data()));
        for(auto im:images) views.push_back(imageView(im,colorFormat,VK_IMAGE_ASPECT_COLOR_BIT));
        depth=image(extent.width,extent.height,depthFormat,VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT,VK_IMAGE_ASPECT_DEPTH_BIT,samples);
        if(samples!=VK_SAMPLE_COUNT_1_BIT) msaa=image(extent.width,extent.height,colorFormat,VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT,VK_IMAGE_ASPECT_COLOR_BIT,samples);
        std::array<VkAttachmentDescription,3> attachments{};
        attachments[0].format=colorFormat;attachments[0].samples=samples;attachments[0].loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR;
        attachments[0].storeOp=samples==VK_SAMPLE_COUNT_1_BIT?VK_ATTACHMENT_STORE_OP_STORE:VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachments[0].stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;attachments[0].stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachments[0].initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;attachments[0].finalLayout=samples==VK_SAMPLE_COUNT_1_BIT?VK_IMAGE_LAYOUT_PRESENT_SRC_KHR:VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        attachments[1].format=depthFormat;attachments[1].samples=samples;attachments[1].loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR;attachments[1].storeOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachments[1].stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;attachments[1].stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachments[1].initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;attachments[1].finalLayout=VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
        attachments[2]=attachments[0];attachments[2].samples=VK_SAMPLE_COUNT_1_BIT;attachments[2].loadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        attachments[2].storeOp=VK_ATTACHMENT_STORE_OP_STORE;attachments[2].finalLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        VkAttachmentReference color{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL},dep{1,VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL},resolve{2,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
        VkSubpassDescription sub{};sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;sub.colorAttachmentCount=1;sub.pColorAttachments=&color;sub.pDepthStencilAttachment=&dep;
        if(samples!=VK_SAMPLE_COUNT_1_BIT) sub.pResolveAttachments=&resolve;
        VkSubpassDependency dependency{};dependency.srcSubpass=VK_SUBPASS_EXTERNAL;dependency.dstSubpass=0;
        // Two CPU frame slots share transient attachments. Serialize their
        // colour/depth writes on this queue; never duplicate full-size MSAA
        // attachments or store pass-local depth/colour back to external memory.
        dependency.srcStageMask=dependency.dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT|VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT|VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
        dependency.srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT|VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        dependency.dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT|VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        VkRenderPassCreateInfo pass{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};pass.attachmentCount=samples==VK_SAMPLE_COUNT_1_BIT?2:3;pass.pAttachments=attachments.data();pass.subpassCount=1;pass.pSubpasses=&sub;pass.dependencyCount=1;pass.pDependencies=&dependency;
        VK_CHECK(vkCreateRenderPass(device,&pass,nullptr,&renderPass));
        for(auto v:views) {
            std::array<VkImageView,3> av{samples==VK_SAMPLE_COUNT_1_BIT?v:msaa.view,depth.view,v};
            VkFramebufferCreateInfo fb{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};fb.renderPass=renderPass;fb.attachmentCount=pass.attachmentCount;fb.pAttachments=av.data();fb.width=extent.width;fb.height=extent.height;fb.layers=1;
            VkFramebuffer f{};VK_CHECK(vkCreateFramebuffer(device,&fb,nullptr,&f));framebuffers.push_back(f);
            VkSemaphoreCreateInfo sem{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};VkSemaphore s{};VK_CHECK(vkCreateSemaphore(device,&sem,nullptr,&s));presented.push_back(s);
        }
        // Compile fixed state variants before animation. No graphics pipeline creation per frame.
        const auto started=std::chrono::steady_clock::now();const auto previous=pipelines.size();
        for(int c=0;c<3;c++) for(int d:{0,1,3}) pipeline(d|(c<<2));
        if(pipelines.size()!=previous) {
            size_t bytes=0;
            if(vkGetPipelineCacheData(device,pipelineCache,&bytes,nullptr)==VK_SUCCESS && bytes>0 && bytes<=MaxPipelineCacheBytes) {
                std::vector<uint8_t> data(bytes);
                if(vkGetPipelineCacheData(device,pipelineCache,&bytes,data.data())==VK_SUCCESS) {
                    data.resize(bytes);std::lock_guard<std::mutex> lock(pipelineStoreMutex);
                    pipelineStoreKey=pipelineKey;pipelineStore=std::move(data);
                    // Private, disposable driver cache; never user artwork. A
                    // temporary file prevents an interrupted write becoming an
                    // apparently valid cache on the next cold launch.
                    if(!cacheFile.empty()) {
                        const auto temporary=cacheFile+".tmp";
                        std::ofstream file(temporary,std::ios::binary|std::ios::trunc);
                        if(file.write(reinterpret_cast<const char*>(pipelineStore.data()),pipelineStore.size())) {
                            file.close();std::rename(temporary.c_str(),cacheFile.c_str());
                        }
                    }
                }
            }
        }
        dirty=false;
        const auto warmupMs=std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-started).count();
        __android_log_print(ANDROID_LOG_INFO,"DiaryBookVulkan","swapchain=%ux%u requested=%ux%u MSAA=%u alpha=%u images=%zu transform=%u currentTransform=%u passes=1 subpasses=1 depthBytes=%llu msaaBytes=%llu depthLazy=%d msaaLazy=%d pipelinesNew=%zu warmupMs=%.3f",extent.width,extent.height,requestedExtent.width,requestedExtent.height,samples,alpha,views.size(),info.preTransform,caps.currentTransform,static_cast<unsigned long long>(depth.bytes),static_cast<unsigned long long>(msaa.bytes),depth.lazy,msaa.lazy,pipelines.size()-previous,warmupMs);
    }
    VkPipeline pipeline(int flags) {
        if(auto found=pipelines.find(flags);found!=pipelines.end()) return found->second;
        VkPipelineShaderStageCreateInfo vs{VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};vs.stage=VK_SHADER_STAGE_VERTEX_BIT;vs.module=vertex;vs.pName="main";
        auto fs=vs;fs.stage=VK_SHADER_STAGE_FRAGMENT_BIT;fs.module=fragment;std::array stages{vs,fs};
        VkVertexInputBindingDescription binding{0,20,VK_VERTEX_INPUT_RATE_VERTEX};
        std::array<VkVertexInputAttributeDescription,2> attributes{{{0,0,VK_FORMAT_R32G32B32_SFLOAT,0},{1,0,VK_FORMAT_R32G32_SFLOAT,12}}};
        VkPipelineVertexInputStateCreateInfo vi{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};vi.vertexBindingDescriptionCount=1;vi.pVertexBindingDescriptions=&binding;vi.vertexAttributeDescriptionCount=2;vi.pVertexAttributeDescriptions=attributes.data();
        VkPipelineInputAssemblyStateCreateInfo ia{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        VkPipelineViewportStateCreateInfo vp{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};vp.viewportCount=1;vp.scissorCount=1;
        VkPipelineRasterizationStateCreateInfo raster{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};raster.polygonMode=VK_POLYGON_MODE_FILL;raster.lineWidth=1;
        raster.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;raster.cullMode=(flags>>2)==1?VK_CULL_MODE_FRONT_BIT:(flags>>2)==2?VK_CULL_MODE_BACK_BIT:VK_CULL_MODE_NONE;
        VkPipelineMultisampleStateCreateInfo multi{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};multi.rasterizationSamples=samples;
        VkPipelineDepthStencilStateCreateInfo ds{VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO};ds.depthTestEnable=bool(flags&1);ds.depthWriteEnable=bool(flags&2);ds.depthCompareOp=VK_COMPARE_OP_LESS_OR_EQUAL;
        // Depth-writing book surfaces have opaque, precomposited artwork.
        // Avoid destination colour reads for every covered MSAA sample. Shadows
        // and fading geometry retain premultiplied blending and no depth writes.
        VkPipelineColorBlendAttachmentState blend{};blend.blendEnable=(flags&2)?VK_FALSE:VK_TRUE;blend.srcColorBlendFactor=blend.srcAlphaBlendFactor=VK_BLEND_FACTOR_ONE;
        blend.dstColorBlendFactor=blend.dstAlphaBlendFactor=VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;blend.colorBlendOp=blend.alphaBlendOp=VK_BLEND_OP_ADD;blend.colorWriteMask=15;
        VkPipelineColorBlendStateCreateInfo cb{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};cb.attachmentCount=1;cb.pAttachments=&blend;
        std::array<VkDynamicState,2> dynamic{VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo dy{VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};dy.dynamicStateCount=2;dy.pDynamicStates=dynamic.data();
        VkGraphicsPipelineCreateInfo info{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};info.stageCount=2;info.pStages=stages.data();info.pVertexInputState=&vi;info.pInputAssemblyState=&ia;
        info.pViewportState=&vp;info.pRasterizationState=&raster;info.pMultisampleState=&multi;info.pDepthStencilState=&ds;info.pColorBlendState=&cb;info.pDynamicState=&dy;
        info.layout=pipelineLayout;info.renderPass=renderPass;VkPipeline p{};VK_CHECK(vkCreateGraphicsPipelines(device,pipelineCache,1,&info,nullptr,&p));pipelines.emplace(flags,p);return p;
    }
public:
    void init(JNIEnv* env,jobject javaSurface,jobject javaAssets,uint32_t width,uint32_t height,const std::string& cachePath) {
        cacheFile=cachePath;
        requestedExtent={width,height};
        window=ANativeWindow_fromSurface(env,javaSurface);if(!window) throw std::runtime_error("No native window");
        VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};app.pApplicationName="QingYe Diary";app.apiVersion=VK_API_VERSION_1_0;
        const char* extensions[]{VK_KHR_SURFACE_EXTENSION_NAME,VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
        VkInstanceCreateInfo ii{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};ii.pApplicationInfo=&app;ii.enabledExtensionCount=2;ii.ppEnabledExtensionNames=extensions;
        VK_CHECK(vkCreateInstance(&ii,nullptr,&instance));
        VkAndroidSurfaceCreateInfoKHR si{VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR};si.window=window;VK_CHECK(vkCreateAndroidSurfaceKHR(instance,&si,nullptr,&surface));
        uint32_t count=0;VK_CHECK(vkEnumeratePhysicalDevices(instance,&count,nullptr));std::vector<VkPhysicalDevice> candidates(count);VK_CHECK(vkEnumeratePhysicalDevices(instance,&count,candidates.data()));
        for(auto candidate:candidates) {
            uint32_t n=0;vkGetPhysicalDeviceQueueFamilyProperties(candidate,&n,nullptr);std::vector<VkQueueFamilyProperties> families(n);vkGetPhysicalDeviceQueueFamilyProperties(candidate,&n,families.data());
            for(uint32_t i=0;i<n;i++) {VkBool32 present=false;VK_CHECK(vkGetPhysicalDeviceSurfaceSupportKHR(candidate,i,surface,&present));if((families[i].queueFlags&VK_QUEUE_GRAPHICS_BIT)&&present) {physical=candidate;queueFamily=i;break;}}
            if(physical) break;
        }
        if(!physical) throw std::runtime_error("No Vulkan graphics/present device");
        float priority=1;VkDeviceQueueCreateInfo qi{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};qi.queueFamilyIndex=queueFamily;qi.queueCount=1;qi.pQueuePriorities=&priority;
        const char* deviceExtensions[]{VK_KHR_SWAPCHAIN_EXTENSION_NAME};VkDeviceCreateInfo di{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};di.queueCreateInfoCount=1;di.pQueueCreateInfos=&qi;di.enabledExtensionCount=1;di.ppEnabledExtensionNames=deviceExtensions;
        VK_CHECK(vkCreateDevice(physical,&di,nullptr,&device));vkGetDeviceQueue(device,queueFamily,0,&queue);
        VkPhysicalDeviceProperties properties;vkGetPhysicalDeviceProperties(physical,&properties);
        pipelineKey=cacheKey(properties);
        std::vector<uint8_t> initialCache;
        { std::lock_guard<std::mutex> lock(pipelineStoreMutex);if(pipelineStoreKey==pipelineKey) initialCache=pipelineStore; }
        if(initialCache.empty() && !cacheFile.empty()) {
            std::ifstream file(cacheFile,std::ios::binary|std::ios::ate);
            const auto length=file.tellg();
            if(length>=std::streamoff(sizeof(VkPipelineCacheHeaderVersionOne)) && length<=std::streamoff(MaxPipelineCacheBytes)) {
                std::vector<uint8_t> data(static_cast<size_t>(length));file.seekg(0);
                if(file.read(reinterpret_cast<char*>(data.data()),data.size())) {
                    VkPipelineCacheHeaderVersionOne header{};std::memcpy(&header,data.data(),sizeof(header));
                    if(header.headerSize>=sizeof(header) && header.headerSize<=data.size() &&
                        header.headerVersion==VK_PIPELINE_CACHE_HEADER_VERSION_ONE && header.vendorID==properties.vendorID &&
                        header.deviceID==properties.deviceID && !std::memcmp(header.pipelineCacheUUID,properties.pipelineCacheUUID,VK_UUID_SIZE)) initialCache=std::move(data);
                }
            }
        }
        VkPipelineCacheCreateInfo pci{VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO};
        pci.initialDataSize=initialCache.size();pci.pInitialData=initialCache.empty()?nullptr:initialCache.data();
        auto cacheResult=vkCreatePipelineCache(device,&pci,nullptr,&pipelineCache);
        if(cacheResult!=VK_SUCCESS && !initialCache.empty()) {
            pci.initialDataSize=0;pci.pInitialData=nullptr;cacheResult=vkCreatePipelineCache(device,&pci,nullptr,&pipelineCache);
        }
        check(cacheResult,"vkCreatePipelineCache");
        __android_log_print(ANDROID_LOG_INFO,"DiaryBookVulkan","pipelineCacheInitialBytes=%zu",initialCache.size());
        const auto support=properties.limits.framebufferColorSampleCounts&properties.limits.framebufferDepthSampleCounts;
        samples=(support&VK_SAMPLE_COUNT_4_BIT)?VK_SAMPLE_COUNT_4_BIT:(support&VK_SAMPLE_COUNT_2_BIT)?VK_SAMPLE_COUNT_2_BIT:VK_SAMPLE_COUNT_1_BIT;
        depthFormat=VK_FORMAT_UNDEFINED;
        for(auto candidate:{VK_FORMAT_D32_SFLOAT,VK_FORMAT_D16_UNORM}) {VkFormatProperties p;vkGetPhysicalDeviceFormatProperties(physical,candidate,&p);if(p.optimalTilingFeatures&VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) {depthFormat=candidate;break;}}
        if(depthFormat==VK_FORMAT_UNDEFINED) throw std::runtime_error("No Vulkan depth format");
        VkCommandPoolCreateInfo pool{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};pool.queueFamilyIndex=queueFamily;pool.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;VK_CHECK(vkCreateCommandPool(device,&pool,nullptr,&commandPool));
        for(auto& frame:frames) {
            VkCommandBufferAllocateInfo ca{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};ca.commandPool=commandPool;ca.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY;ca.commandBufferCount=1;VK_CHECK(vkAllocateCommandBuffers(device,&ca,&frame.command));
            VkFenceCreateInfo fi{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};fi.flags=VK_FENCE_CREATE_SIGNALED_BIT;VK_CHECK(vkCreateFence(device,&fi,nullptr,&frame.fence));
            VkSemaphoreCreateInfo sem{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};VK_CHECK(vkCreateSemaphore(device,&sem,nullptr,&frame.acquired));
        }
        uniformStride=(UniformBytes+properties.limits.minUniformBufferOffsetAlignment-1)&~(properties.limits.minUniformBufferOffsetAlignment-1);
        // Each in-flight command buffer owns its matrix/material range. The CPU
        // cannot overwrite uniforms still being read by the preceding GPU frame.
        uniform=buffer(VkDeviceSize(uniformStride)*MaxDraws*FramesInFlight,VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,true);
        std::array<VkDescriptorSetLayoutBinding,2> bindings{{{0,VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC,1,VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT,nullptr},{1,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,1,VK_SHADER_STAGE_FRAGMENT_BIT,nullptr}}};
        VkDescriptorSetLayoutCreateInfo li{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};li.bindingCount=2;li.pBindings=bindings.data();VK_CHECK(vkCreateDescriptorSetLayout(device,&li,nullptr,&descriptorLayout));
        std::array<VkDescriptorPoolSize,2> sizes{{{VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC,64},{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,64}}};
        VkDescriptorPoolCreateInfo dpi{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};dpi.flags=VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;dpi.maxSets=64;dpi.poolSizeCount=2;dpi.pPoolSizes=sizes.data();VK_CHECK(vkCreateDescriptorPool(device,&dpi,nullptr,&descriptorPool));
        VkPipelineLayoutCreateInfo pli{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};pli.setLayoutCount=1;pli.pSetLayouts=&descriptorLayout;VK_CHECK(vkCreatePipelineLayout(device,&pli,nullptr,&pipelineLayout));
        VkSamplerCreateInfo spi{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};spi.magFilter=spi.minFilter=VK_FILTER_LINEAR;spi.mipmapMode=VK_SAMPLER_MIPMAP_MODE_LINEAR;spi.addressModeU=spi.addressModeV=spi.addressModeW=VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        VK_CHECK(vkCreateSampler(device,&spi,nullptr,&sampler));
        auto assets=AAssetManager_fromJava(env,javaAssets);if(!assets) throw std::runtime_error("No shader assets");
        vertex=shader(assets,"shaders/book.vert.spv");fragment=shader(assets,"shaders/book.frag.spv");
        createSwapchain();
        __android_log_print(ANDROID_LOG_INFO,"DiaryBookVulkan","backend=Vulkan device=%s api=%u.%u.%u frameSlots=%u uniformSlots=%u attachments=shared-serialized",properties.deviceName,VK_VERSION_MAJOR(properties.apiVersion),VK_VERSION_MINOR(properties.apiVersion),VK_VERSION_PATCH(properties.apiVersion),FramesInFlight,FramesInFlight);
    }
    void resize(uint32_t width,uint32_t height) {requestedExtent={width,height};dirty=true;}
    int mesh(const float* data,size_t bytes,const uint16_t* indices,size_t count) {
        Mesh m;
        try {m.vertices=buffer(bytes,VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,true);std::memcpy(m.vertices.mapped,data,bytes);
            m.indices=buffer(count*2,VK_BUFFER_USAGE_INDEX_BUFFER_BIT,true);std::memcpy(m.indices.mapped,indices,count*2);m.count=count;
        } catch(...) {free(m.vertices);free(m.indices);throw;}
        const auto id=nextId++;meshes.emplace(id,m);return id;
    }
    int texture(const void* pixels,uint32_t width,uint32_t height,uint32_t stride) {
        collectUploads();
        Texture t;Buffer staging;VkCommandBuffer upload{};VkFence uploadFence{};
        try {
            const auto needed=VkDeviceSize(width)*height*4;
            auto cached=std::min_element(idleUploads.begin(),idleUploads.end(),[needed](const Upload& a,const Upload& b) {
                return (a.staging.capacity>=needed?a.staging.capacity:UINT64_MAX)<(b.staging.capacity>=needed?b.staging.capacity:UINT64_MAX);
            });
            if(cached!=idleUploads.end() && cached->staging.capacity>=needed) {
                staging=cached->staging;upload=cached->command;uploadFence=cached->fence;
                idleUploadBytes-=staging.capacity;idleUploads.erase(cached);stagingReuses++;
                VK_CHECK(vkResetCommandBuffer(upload,0));VK_CHECK(vkResetFences(device,1,&uploadFence));
            } else {
                // 64 KiB bins keep the 3.375 MiB cover and 4.625 MiB paper
                // together in the 8 MiB pool; power-of-two bins cannot.
                constexpr VkDeviceSize block=64u*1024u;
                const auto capacity=((needed+block-1)/block)*block;
                staging=buffer(capacity,VK_BUFFER_USAGE_TRANSFER_SRC_BIT,true);stagingAllocations++;
            }
            for(uint32_t y=0;y<height;y++) std::memcpy(static_cast<char*>(staging.mapped)+size_t(y)*width*4,static_cast<const char*>(pixels)+size_t(y)*stride,width*4);
            t.image=image(width,height,VK_FORMAT_R8G8B8A8_UNORM,VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT,VK_IMAGE_ASPECT_COLOR_BIT);
            if(!upload) {VkCommandBufferAllocateInfo ai{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};ai.commandPool=commandPool;ai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY;ai.commandBufferCount=1;VK_CHECK(vkAllocateCommandBuffers(device,&ai,&upload));}
            VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};begin.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;VK_CHECK(vkBeginCommandBuffer(upload,&begin));
            VkImageMemoryBarrier barrier{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};barrier.image=t.image.image;barrier.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};barrier.srcQueueFamilyIndex=barrier.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
            barrier.oldLayout=VK_IMAGE_LAYOUT_UNDEFINED;barrier.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;barrier.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
            vkCmdPipelineBarrier(upload,VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,nullptr,0,nullptr,1,&barrier);
            VkBufferImageCopy copy{};copy.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};copy.imageExtent={width,height,1};vkCmdCopyBufferToImage(upload,staging.buffer,t.image.image,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,1,&copy);
            barrier.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;barrier.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;barrier.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;barrier.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
            vkCmdPipelineBarrier(upload,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,0,0,nullptr,0,nullptr,1,&barrier);
            VK_CHECK(vkEndCommandBuffer(upload));VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};submit.commandBufferCount=1;submit.pCommandBuffers=&upload;
            if(!uploadFence) {VkFenceCreateInfo fenceInfo{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};VK_CHECK(vkCreateFence(device,&fenceInfo,nullptr,&uploadFence));}
            // The transfer barrier also covers later fragment reads on this same
            // queue. Keep the staging/command alive; never drain the queue here.
            VK_CHECK(vkQueueSubmit(queue,1,&submit,uploadFence));
            VkDescriptorSetAllocateInfo da{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};da.descriptorPool=descriptorPool;da.descriptorSetCount=1;da.pSetLayouts=&descriptorLayout;VK_CHECK(vkAllocateDescriptorSets(device,&da,&t.descriptor));
            VkDescriptorBufferInfo ub{uniform.buffer,0,UniformBytes};VkDescriptorImageInfo im{sampler,t.image.view,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
            std::array<VkWriteDescriptorSet,2> writes{};
            writes[0]={VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};writes[0].dstSet=t.descriptor;writes[0].dstBinding=0;writes[0].descriptorCount=1;writes[0].descriptorType=VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;writes[0].pBufferInfo=&ub;
            writes[1]={VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};writes[1].dstSet=t.descriptor;writes[1].dstBinding=1;writes[1].descriptorCount=1;writes[1].descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;writes[1].pImageInfo=&im;
            vkUpdateDescriptorSets(device,2,writes.data(),0,nullptr);
            uploads.push_back({staging,upload,uploadFence});staging={};upload={};uploadFence={};
            textureUploads++;peakUploads=std::max(peakUploads,uint64_t(uploads.size()));
        } catch(...) {vkDeviceWaitIdle(device);if(upload) vkFreeCommandBuffers(device,commandPool,1,&upload);if(uploadFence) vkDestroyFence(device,uploadFence,nullptr);free(staging);freeTexture(t);throw;}
        const auto id=nextId++;textures.emplace(id,t);return id;
    }
    void deleteTexture(int id) {
        auto it=textures.find(id);if(it==textures.end()) return;
        retiredTextures.push_back(it->second);textures.erase(it);
    }
    std::array<jlong,6> stats() const {
        return {jlong(textureUploads),jlong(stagingAllocations),jlong(stagingReuses),jlong(idleUploadBytes),jlong(peakUploads),jlong(uploads.size())};
    }
    bool frame(const Command* draws,uint32_t count) {
        if(count>MaxDraws) throw std::runtime_error("Invalid Vulkan draw count");
        if(dirty) createSwapchain();
        auto& slot=frames[frameSlot];
        { Trace trace("Book.VkFence");VK_CHECK(vkWaitForFences(device,1,&slot.fence,VK_TRUE,UINT64_MAX)); }
        completedSerial=std::max(completedSerial,slot.serial);
        collectUploads();
        collectRetiredTextures();
        const auto command=slot.command;
        uint32_t index=0;VkResult acquire;
        { Trace trace("Book.VkAcquire");acquire=vkAcquireNextImageKHR(device,swapchain,UINT64_MAX,slot.acquired,VK_NULL_HANDLE,&index); }
        if(acquire==VK_ERROR_OUT_OF_DATE_KHR) {dirty=true;return false;}
        if(acquire!=VK_SUBOPTIMAL_KHR) check(acquire,"vkAcquireNextImageKHR");
        dirty=acquire==VK_SUBOPTIMAL_KHR;
        VK_CHECK(vkResetCommandBuffer(command,0));
        VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};begin.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;VK_CHECK(vkBeginCommandBuffer(command,&begin));
        std::array<VkClearValue,3> clear{};clear[1].depthStencil={1,0};
        VkRenderPassBeginInfo rp{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};rp.renderPass=renderPass;rp.framebuffer=framebuffers[index];rp.renderArea={{0,0},extent};rp.clearValueCount=samples==VK_SAMPLE_COUNT_1_BIT?2:3;rp.pClearValues=clear.data();
        vkCmdBeginRenderPass(command,&rp,VK_SUBPASS_CONTENTS_INLINE);
        VkViewport viewport{0,0,float(extent.width),float(extent.height),0,1};vkCmdSetViewport(command,0,1,&viewport);
        VkRect2D scissor{{0,0},extent};vkCmdSetScissor(command,0,1,&scissor);
        VkPipeline boundPipeline=VK_NULL_HANDLE;
        int boundMesh=-1;
        for(uint32_t i=0;i<count;i++) {
            const auto& d=draws[i];auto mesh=meshes.find(d.mesh);auto tex=textures.find(d.texture);
            if(mesh==meshes.end()||tex==textures.end()) throw std::runtime_error("Missing Vulkan mesh/texture");
            if(d.flags<0||d.flags>11) throw std::runtime_error("Invalid Vulkan draw state");
            const uint32_t offset=(frameSlot*MaxDraws+i)*uniformStride;
            std::memcpy(static_cast<char*>(uniform.mapped)+offset,d.uniform,UniformBytes);
            const auto nextPipeline=pipeline(d.flags);
            if(nextPipeline!=boundPipeline) { vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_GRAPHICS,nextPipeline);boundPipeline=nextPipeline; }
            vkCmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_GRAPHICS,pipelineLayout,0,1,&tex->second.descriptor,1,&offset);
            if(d.mesh!=boundMesh) {
                VkDeviceSize zero=0;vkCmdBindVertexBuffers(command,0,1,&mesh->second.vertices.buffer,&zero);vkCmdBindIndexBuffer(command,mesh->second.indices.buffer,0,VK_INDEX_TYPE_UINT16);
                boundMesh=d.mesh;
            }
            vkCmdDrawIndexed(command,mesh->second.count,1,0,0,0);
        }
        vkCmdEndRenderPass(command);VK_CHECK(vkEndCommandBuffer(command));
        const VkPipelineStageFlags stage=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};submit.waitSemaphoreCount=1;submit.pWaitSemaphores=&slot.acquired;submit.pWaitDstStageMask=&stage;
        submit.commandBufferCount=1;submit.pCommandBuffers=&command;submit.signalSemaphoreCount=1;submit.pSignalSemaphores=&presented[index];
        VK_CHECK(vkResetFences(device,1,&slot.fence));VK_CHECK(vkQueueSubmit(queue,1,&submit,slot.fence));
        slot.serial=++submittedSerial;
        // This later submission fences all earlier draws AND uploads on the
        // same queue. A two-slot renderer must not free on the very next fence.
        for(auto& texture:retiredTextures) inFlightRetiredTextures.push_back({texture,slot.serial});
        retiredTextures.clear();frameSlot=(frameSlot+1)%FramesInFlight;
        VkPresentInfoKHR pi{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};pi.waitSemaphoreCount=1;pi.pWaitSemaphores=&presented[index];pi.swapchainCount=1;pi.pSwapchains=&swapchain;pi.pImageIndices=&index;
        VkResult result;
        { Trace trace("Book.VkPresent");result=vkQueuePresentKHR(queue,&pi); }
        if(result==VK_ERROR_OUT_OF_DATE_KHR||result==VK_SUBOPTIMAL_KHR) {dirty=true;return false;}
        check(result,"vkQueuePresentKHR");return true;
    }
    ~Engine() {
        if(device) {
            vkDeviceWaitIdle(device);collectUploads();destroySwapchain();destroyPipelines();
            for(auto& item:idleUploads) freeUpload(item);idleUploads.clear();idleUploadBytes=0;
            for(auto& t:retiredTextures) freeTexture(t);
            for(auto& t:inFlightRetiredTextures) freeTexture(t.texture);
            if(pipelineCache) vkDestroyPipelineCache(device,pipelineCache,nullptr);
            for(auto& [_,t]:textures) free(t.image);
            for(auto& [_,m]:meshes) {free(m.vertices);free(m.indices);}
            free(uniform);
            if(vertex) vkDestroyShaderModule(device,vertex,nullptr);if(fragment) vkDestroyShaderModule(device,fragment,nullptr);
            if(sampler) vkDestroySampler(device,sampler,nullptr);if(pipelineLayout) vkDestroyPipelineLayout(device,pipelineLayout,nullptr);
            if(descriptorPool) vkDestroyDescriptorPool(device,descriptorPool,nullptr);if(descriptorLayout) vkDestroyDescriptorSetLayout(device,descriptorLayout,nullptr);
            for(auto& frame:frames) {
                if(frame.fence) vkDestroyFence(device,frame.fence,nullptr);
                if(frame.acquired) vkDestroySemaphore(device,frame.acquired,nullptr);
            }
            if(commandPool) vkDestroyCommandPool(device,commandPool,nullptr);vkDestroyDevice(device,nullptr);
        }
        if(surface) vkDestroySurfaceKHR(instance,surface,nullptr);if(instance) vkDestroyInstance(instance,nullptr);if(window) ANativeWindow_release(window);
    }
};
Engine& engine(jlong handle) {if(!handle) throw std::runtime_error("Vulkan renderer is closed");return *reinterpret_cast<Engine*>(handle);}
void failure(JNIEnv* env,const std::exception& error) {
    __android_log_print(ANDROID_LOG_ERROR,"DiaryBookVulkan","%s",error.what());env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),error.what());
}
}
#define JNI_METHOD(name) Java_com_qingyu_personalaios_feature_diary_VulkanBookBackend_##name
extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(nativeCreate)(JNIEnv* env,jobject,jobject surface,jobject assets,jint width,jint height,jstring cachePath) {
    try {
        if(width<=0||height<=0) throw std::runtime_error("Invalid Vulkan surface size");
        std::string path;
        if(cachePath) {const char* value=env->GetStringUTFChars(cachePath,nullptr);if(value) {path=value;env->ReleaseStringUTFChars(cachePath,value);} }
        auto e=std::make_unique<Engine>();e->init(env,surface,assets,width,height,path);return reinterpret_cast<jlong>(e.release());
    }catch(const std::exception& ex){failure(env,ex);return 0;}
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(nativeDestroy)(JNIEnv*,jobject,jlong handle) {delete reinterpret_cast<Engine*>(handle);}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(nativeResize)(JNIEnv* env,jobject,jlong handle,jint width,jint height) {try{if(width<=0||height<=0) throw std::runtime_error("Invalid Vulkan surface size");engine(handle).resize(width,height);}catch(const std::exception& ex){failure(env,ex);}}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(nativeMesh)(JNIEnv* env,jobject,jlong handle,jfloatArray vertices,jshortArray indices) {
    try {
        auto nv=env->GetArrayLength(vertices),ni=env->GetArrayLength(indices);if(nv<=0||nv%5||ni<=0||ni%3) throw std::runtime_error("Invalid mesh arrays");
        std::vector<float> v(nv);std::vector<jshort> i(ni);env->GetFloatArrayRegion(vertices,0,nv,v.data());env->GetShortArrayRegion(indices,0,ni,i.data());
        return engine(handle).mesh(v.data(),v.size()*4,reinterpret_cast<uint16_t*>(i.data()),i.size());
    }catch(const std::exception& ex){failure(env,ex);return 0;}
}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(nativeTexture)(JNIEnv* env,jobject,jlong handle,jobject bitmap) {
    void* pixels=nullptr;
    try {
        AndroidBitmapInfo info{};if(AndroidBitmap_getInfo(env,bitmap,&info)!=ANDROID_BITMAP_RESULT_SUCCESS||info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888) throw std::runtime_error("Expected RGBA bitmap");
        if(AndroidBitmap_lockPixels(env,bitmap,&pixels)!=ANDROID_BITMAP_RESULT_SUCCESS) throw std::runtime_error("Cannot lock page bitmap");
        const auto id=engine(handle).texture(pixels,info.width,info.height,info.stride);AndroidBitmap_unlockPixels(env,bitmap);return id;
    }catch(const std::exception& ex){if(pixels) AndroidBitmap_unlockPixels(env,bitmap);failure(env,ex);return 0;}
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(nativeDeleteTexture)(JNIEnv* env,jobject,jlong handle,jint id) {try{engine(handle).deleteTexture(id);}catch(const std::exception& ex){failure(env,ex);}}
extern "C" JNIEXPORT jboolean JNICALL JNI_METHOD(nativeFrame)(JNIEnv* env,jobject,jlong handle,jobject commands,jint count) {
    try {auto data=env->GetDirectBufferAddress(commands);auto size=env->GetDirectBufferCapacity(commands);
        if(!data||count<0||size<jlong(count)*CommandBytes) throw std::runtime_error("Invalid draw buffer");
        return engine(handle).frame(static_cast<Command*>(data),count);
    }catch(const std::exception& ex){failure(env,ex);return false;}
}
extern "C" JNIEXPORT jlongArray JNICALL JNI_METHOD(nativeStats)(JNIEnv* env,jobject,jlong handle) {
    try {const auto values=engine(handle).stats();auto result=env->NewLongArray(values.size());if(result) env->SetLongArrayRegion(result,0,values.size(),values.data());return result;}
    catch(const std::exception& ex){failure(env,ex);return nullptr;}
}
