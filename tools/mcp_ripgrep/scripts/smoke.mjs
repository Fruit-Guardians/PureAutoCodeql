import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

const transport = new StdioClientTransport({
  command: process.execPath,
  args: ["dist/index.js"],
});
const client = new Client({ name: "smoke-test", version: "1.0.0" });

try {
  await client.connect(transport);
  const response = await client.listTools();
  const names = new Set(response.tools.map((tool) => tool.name));
  if (!names.has("search") || !names.has("advanced-search")) {
    throw new Error("MCP tool negotiation did not return expected tools");
  }
} finally {
  await client.close();
}
