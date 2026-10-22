#pragma once
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>
#define FILE_READ "r"
#define FILE_WRITE "w"

class File {
 public:
  std::shared_ptr<std::vector<uint8_t>> data;
  size_t position = 0;
  File() = default;
  explicit File(std::shared_ptr<std::vector<uint8_t>> value) : data(value) {}
  explicit operator bool() const { return static_cast<bool>(data); }
  size_t size() const { return data ? data->size() : 0; }
  int available() const { return static_cast<int>(size() - position); }
  bool seek(size_t pos) { if (!data || pos > size()) return false; position = pos; return true; }
  size_t read(uint8_t *out, size_t count) {
    if (!data) return 0;
    count = std::min(count, size() - position);
    std::memcpy(out, data->data() + position, count); position += count; return count;
  }
  size_t write(const uint8_t *input, size_t count) {
    if (!data) return 0;
    data->resize(std::max(size(), position + count));
    std::memcpy(data->data() + position, input, count); position += count; return count;
  }
  void flush() {}
};
namespace fs {
class FS {
 public:
  std::unordered_map<std::string, std::shared_ptr<std::vector<uint8_t>>> files;
  bool exists(const char *path) { return files.count(path) != 0; }
  bool remove(const char *path) { return files.erase(path) != 0; }
  File open(const char *path, const char *mode) {
    if (*mode == 'w') files[path] = std::make_shared<std::vector<uint8_t>>();
    const auto item = files.find(path);
    return item == files.end() ? File() : File(item->second);
  }
};
}
