// Standalone Android check: frozen fixture input only, no app data and no network.
#include "jev.hpp"
#include <cstdint>
#include <iomanip>
#include <iostream>
#include <stdexcept>

namespace {
uint32_t count() {
    unsigned char bytes[4];
    if (!std::cin.read(reinterpret_cast<char *>(bytes), 4)) throw std::runtime_error("Truncated fixture input");
    return static_cast<uint32_t>(bytes[0]) | (static_cast<uint32_t>(bytes[1]) << 8) |
           (static_cast<uint32_t>(bytes[2]) << 16) | (static_cast<uint32_t>(bytes[3]) << 24);
}
std::string field() {
    const auto size = count();
    if (size > 1024 * 1024) throw std::runtime_error("Fixture field exceeds budget");
    std::string result(size, '\0');
    if (!std::cin.read(result.data(), size)) throw std::runtime_error("Truncated fixture field");
    return result;
}
}
int main(int argc, char **argv) {
    if (argc != 2) { std::cerr << "Usage: kawkab_jev_check MODEL\n"; return 2; }
    try {
        while (std::cin.peek() != std::char_traits<char>::eof()) {
            const auto state = field();
            const auto question = field();
            const auto total = count();
            if (total < 2 || total > 20) throw std::runtime_error("Invalid option count");
            std::vector<std::string> options;
            for (uint32_t i = 0; i < total; ++i) options.push_back(field());
            const auto scores = kawkab::verdicts(argv[1], state, question, options);
            for (size_t i = 0; i < scores.size(); ++i) {
                if (i) std::cout << ' ';
                std::cout << std::setprecision(17) << scores[i];
            }
            std::cout << '\n';
        }
        kawkab::release_verdicts();
        return 0;
    } catch (const std::exception &error) {
        kawkab::release_verdicts();
        std::cerr << error.what() << '\n';
        return 1;
    }
}
