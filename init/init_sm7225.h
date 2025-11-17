#ifndef INIT_SEC_H
#define INIT_SEC_H

#include <string.h>

enum device_variant {
    VARIANT_M236B = 0,
    VARIANT_E236B,
    VARIANT_MAX
};

typedef struct {
    std::string model;
    std::string codename;
} variant;

static const variant international_models_m23 = {
    .model = "SM-M236B",
    .codename = "m23xq"
};

static const variant india_models_f23 = {
    .model = "SM-E236B",
    .codename = "m23xq"
};

static const variant *all_variants[VARIANT_MAX] = {
    &international_models_m23,
    &india_models_f23,
};

#endif // INIT_SEC_H
