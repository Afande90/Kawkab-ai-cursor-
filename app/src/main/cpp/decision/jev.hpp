// Kawkab verdict readout, shared by the JNI adapter and the on-phone check.
#pragma once
#include <string>
#include <vector>
namespace kawkab {
std::vector<double> verdicts(const std::string &path, const std::string &state,
                             const std::string &question, const std::vector<std::string> &options);
void release_verdicts();
}
