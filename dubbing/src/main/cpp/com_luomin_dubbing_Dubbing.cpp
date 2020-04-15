
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ndk-bundle/sysroot/usr/include/assert.h>
#include "com_luomin_dubbing_Dubbing.h"
#include "include/fdk-aac/aacenc_lib.h"
#include "include/fdk-aac/aacdecoder_lib.h"


int getADTSframe(unsigned char *buffer, int buf_size, unsigned char *data, int *data_size) {
    int size = 0;

    if (!buffer || !data || !data_size) {
        return -1;
    }

    while (1) {
        if (buf_size < 7) {
            return -1;
        }
        //Sync words
        if ((buffer[0] == 0xff) && ((buffer[1] & 0xf0) == 0xf0)) {
            size |= ((buffer[3] & 0x03) << 11);     //high 2 bit
            size |= buffer[4] << 3;                //middle 8 bit
            size |= ((buffer[5] & 0xe0) >> 5);        //low 3bit
            break;
        }
        --buf_size;
        ++buffer;
    }

    if (buf_size < size) {
        return 1;
    }

    memcpy(data, buffer, size);
    *data_size = size;

    return 0;
}

int g_decode_aac_profile = AOT_AAC_LC;

/*
 * Class:     com_luomin_dubbing_Dubbing
 * Method:    fdkdecodec
 * Signature: (Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_luomin_dubbing_Dubbing_fdkdecodec
        (JNIEnv *jniEnv, jclass jclass, jstring pcmfile, jstring aacfile) {

    jboolean isCopy;
    const char *tempPcmFile = jniEnv->GetStringUTFChars(pcmfile, &isCopy);
    const char *tempAacFile = jniEnv->GetStringUTFChars(aacfile, &isCopy);


    AAC_DECODER_ERROR err = AAC_DEC_OK;
    HANDLE_AACDECODER decoder = aacDecoder_Open(TT_MP4_ADTS, 1);

    // 设置ASC信息
    if (g_decode_aac_profile == AOT_ER_AAC_ELD) {
        UCHAR conf[] = {0xF8, 0xF0, 0x20, 0x00};  //AAL-ELD 16000kHz MONO
        UCHAR *conf_array[1] = {conf};
        UINT length = 4;
        err = aacDecoder_ConfigRaw(decoder, conf_array, &length);
    } else if (g_decode_aac_profile == AOT_AAC_LC) {
        // 0001 0010 0001 0000
        UCHAR conf[] = {0x12, 0x10};  //AAL-LC 44100kHz STEREO
        UCHAR *conf_array[1] = {conf};
        UINT length = 2;
        err = aacDecoder_ConfigRaw(decoder, conf_array, &length);
    }

    // 获取信息
    CStreamInfo *info = aacDecoder_GetStreamInfo(decoder);
    int max_frame_size;
    if (g_decode_aac_profile == AOT_ER_AAC_ELD) {
        max_frame_size = 512;
    } else if (g_decode_aac_profile == AOT_AAC_LC) {
        max_frame_size = 1024;
    }

    int pcm_buffer_size = max_frame_size * 1024;
    size_t count = sizeof(INT_PCM) * pcm_buffer_size;
    INT_PCM *pcm_buffer = (INT_PCM *) malloc(count);

    FILE *output_fp = fopen(tempPcmFile, "wb");
    FILE *input_fp = fopen(tempAacFile, "rb");

    int data_size = 0;
    int size = 0;
    int cnt = 0;
    int offset = 0;

    //    unsigned char* aacframe = (unsigned char*)malloc(1024 * 5);
    unsigned char aacframeArr[1024 * 5];
    unsigned char *aacframe = aacframeArr;
    unsigned char *aacbuffer = (unsigned char *) malloc(1024 * 1024);

    UINT bytes_valid;

//    while (!feof(input_fp)) {
        data_size = fread(aacbuffer + offset, 1, 1024 * 1024 - offset, input_fp);
        unsigned char *input_data = aacbuffer;


        while (1) {
            int ret = getADTSframe(input_data, data_size, aacframe, &size);
            if (ret == -1) {
                break;
            } else if (ret == 1) {
                memcpy(aacbuffer, input_data, data_size);
                offset = data_size;
                break;
            }
            bytes_valid = size;
            err = aacDecoder_Fill(decoder, &(aacframe),
                                  reinterpret_cast<const UINT *>(&(bytes_valid)), &bytes_valid);

            CStreamInfo *info = aacDecoder_GetStreamInfo(decoder);
            // 解码
            err = aacDecoder_DecodeFrame(decoder, pcm_buffer, pcm_buffer_size / sizeof(INT_PCM), 0);
            data_size -= size;
            input_data += size;

            if (err != AAC_DEC_OK) {
                continue;
            }

            info = aacDecoder_GetStreamInfo(decoder);
            int output_pcm_bytes = info->frameSize * info->numChannels * 2;

            // pcm 数据写入文件
            if (output_fp) {
                size_t ws = fwrite(pcm_buffer, output_pcm_bytes, 1, output_fp);
            }
        }
//    }

    free(aacbuffer);
    //    if(aacframe){
    //        free(aacframe);
    //    }

    // 释放资源
    if (output_fp) {
        fclose(output_fp);
    }
    if (pcm_buffer) {
        free(pcm_buffer);
    }
    //    release_aac_raw_buf_list();
    aacDecoder_Close(decoder);


    jniEnv->ReleaseStringUTFChars(pcmfile, tempPcmFile);
    jniEnv->ReleaseStringUTFChars(aacfile, tempAacFile);
    return 0;
}

/*
 * Class:     com_luomin_dubbing_Dubbing
 * Method:    mixedpcm
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_luomin_dubbing_Dubbing_mixedpcm
        (JNIEnv *jniEnv, jclass jclass, jstring backgroundPcm,
         jstring peoplePcm, jstring outPcm) {

    jboolean isCopy;
    const char *tempBackgroundFile = jniEnv->GetStringUTFChars(backgroundPcm, &isCopy);
    const char *tempPeoplePcm2File = jniEnv->GetStringUTFChars(peoplePcm, &isCopy);
    const char *tempOutFile = jniEnv->GetStringUTFChars(outPcm, &isCopy);

    FILE *fpBackground = fopen(tempBackgroundFile, "rb");
    FILE *fpPeople = fopen(tempPeoplePcm2File, "rb");
    FILE *fpOut = fopen(tempOutFile, "wb");


    unsigned char backgroundFrame[4];
    unsigned char peopleFrame[4];
    unsigned char peopleFrameNull[4];
    memset(peopleFrameNull, 0, 4);

    int count = 0;
    while (fread(backgroundFrame, 4, 1, fpBackground)) {
        if (count % 2 == 0) {
            fwrite(backgroundFrame, 1, 4, fpOut);

        }
        if (fread(peopleFrame, 4, 1, fpPeople) != -1) {
            if (count % 2 == 0) {
                fwrite(peopleFrame, 1, 4, fpOut);
            }
        } else {
            if (count % 2 == 0) {
                fwrite(peopleFrameNull, 1, 4, fpOut);
            }
        }

        count++;

        //        fwrite(backgroundFrame, 1, 4, fpOut);
        //        if (fread(peopleFrame, 4, 1, fpPeople) != -1) {
        //            fwrite(peopleFrame, 1, 4, fpOut);
        //        } else {
        //            fwrite(peopleFrameNull, 1, 4, fpOut);
        //        }
    }

    if (count % 2 != 0) {
        fwrite(peopleFrameNull, 1, 4, fpOut);
    }

    fclose(fpBackground);
    fclose(fpPeople);
    fclose(fpOut);

    jniEnv->ReleaseStringUTFChars(backgroundPcm, tempBackgroundFile);
    jniEnv->ReleaseStringUTFChars(peoplePcm, tempPeoplePcm2File);
    jniEnv->ReleaseStringUTFChars(outPcm, tempOutFile);
    return 0;
}



/*
 * Class:     com_luomin_dubbing_Dubbing
 * Method:    fdkencodec
 * Signature: (Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_luomin_dubbing_Dubbing_fdkencodec
        (JNIEnv *jniEnv, jclass jclass, jstring pcmfile,
         jstring aacfile) {


    jboolean isCopy;
    const char *tempPcmFile = jniEnv->GetStringUTFChars(pcmfile, &isCopy);
    const char *tempAacFile = jniEnv->GetStringUTFChars(aacfile, &isCopy);

    FILE *fpIn = fopen(tempPcmFile, "rb");
    FILE *fpOut = fopen(tempAacFile, "wb");

    HANDLE_AACENCODER handle;
    if (aacEncOpen(&handle, 0, 2) != AACENC_OK) {
        return -1;
    }
    int aot = 2;
    if (aacEncoder_SetParam(handle, AACENC_AOT, aot) != AACENC_OK) {
        fprintf(stderr, "Unable to set the AOT\n");
        return -1;
    }
    if (aacEncoder_SetParam(handle, AACENC_SAMPLERATE, 44100) != AACENC_OK) {
//    if (aacEncoder_SetParam(handle, AACENC_SAMPLERATE, 88200) != AACENC_OK) {
        fprintf(stderr, "Unable to set the SAMPLERATE\n");
        return -1;
    }
    if (aacEncoder_SetParam(handle, AACENC_CHANNELMODE, MODE_2) != AACENC_OK) {
        fprintf(stderr, "Unable to set the CHANNELMODE\n");
        return -1;
    }
    if (aacEncoder_SetParam(handle, AACENC_BITRATE, 176400) != AACENC_OK) {
//    if (aacEncoder_SetParam(handle, AACENC_BITRATE, 352800) != AACENC_OK) {
        fprintf(stderr, "Unable to set the BITRATE\n");
        return -1;
    }
    if (aacEncoder_SetParam(handle, AACENC_TRANSMUX, 2) != AACENC_OK) {
        fprintf(stderr, "Unable to set the TRANSMUX\n");
        return -1;
    }

    if (aacEncEncode(handle, NULL, NULL, NULL, NULL) != AACENC_OK) {
        return -1;
    }

    AACENC_InfoStruct encInfo;
    aacEncInfo(handle, &encInfo);

/*******************************https://blog.csdn.net/dong_beijing/article/details/87935397 实现*****************************************************/

    int cnt = 0;

    int8_t *frameMax = static_cast<int8_t *>(malloc(encInfo.frameLength));
    AACENC_BufDesc in_buf = {0};
    AACENC_BufDesc out_buf = {0};
    AACENC_InArgs in_args = {0};
    AACENC_OutArgs out_args = {0};
    int in_identifier = IN_AUDIO_DATA;
    int in_size, in_elem_size;
    int out_identifier = OUT_BITSTREAM_DATA;
    int out_size, out_elem_size;
    int read, i;
    void *in_ptr, *out_ptr;
    unsigned char outbuf[encInfo.frameLength];
    AACENC_ERROR err;

    while ((read = fread(frameMax, 1, 1024, fpIn)) != -1) {
        cnt++;

        in_ptr = frameMax;
        in_size = read;
        in_elem_size = 2;

        in_args.numInSamples = read <= 0 ? -1 : read / 2;
        in_buf.numBufs = 1;
        in_buf.bufs = &in_ptr;
        in_buf.bufferIdentifiers = &in_identifier;
        in_buf.bufSizes = &in_size;
        in_buf.bufElSizes = &in_elem_size;

        out_ptr = outbuf;
        out_size = sizeof(outbuf);
        out_elem_size = 1;
        out_buf.numBufs = 1;
        out_buf.bufs = &out_ptr;
        out_buf.bufferIdentifiers = &out_identifier;
        out_buf.bufSizes = &out_size;
        out_buf.bufElSizes = &out_elem_size;

        if ((err = aacEncEncode(handle, &in_buf, &out_buf, &in_args, &out_args)) != AACENC_OK) {
            if (err == AACENC_ENCODE_EOF)
                break;
            fprintf(stderr, "Encoding failed\n");
            return -1;
        }
        if (out_args.numOutBytes == 0)
            continue;
        fwrite(outbuf, 1, static_cast<size_t>(out_args.numOutBytes), fpOut);
//        fwrite(&(out_args.numOutBytes), 1, 2, lengthHandle);
    }

/*******************************https://blog.csdn.net/dong_beijing/article/details/87935397 实现结束*****************************************************/
    aacEncClose(&handle);
    fclose(fpIn);
    fclose(fpOut);
    free(frameMax);
    jniEnv->ReleaseStringUTFChars(pcmfile, tempPcmFile);
    jniEnv->ReleaseStringUTFChars(aacfile, tempAacFile);
    return 1;

}

