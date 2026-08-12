import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

const baseUrl = (process.env.AGENT_MCP_BASE_URL || 'http://localhost:8086').replace(/\/$/, '');
const internalToken = process.env.AGENT_INTERNAL_TOKEN;
const userId = String(process.env.AGENT_MCP_USER_ID || 1);
const searchQuery = process.env.AGENT_MCP_SEARCH_QUERY || '示例检索';
if (!internalToken) throw new Error('Set AGENT_INTERNAL_TOKEN; it is never printed.');
if (!/^\d+$/.test(userId) || BigInt(userId) <= 0n) throw new Error('AGENT_MCP_USER_ID must be positive.');

const client = new Client({ name: 'reader-agent-external-sdk-check', version: '1.0.0' });
const transport = new StreamableHTTPClientTransport(new URL(`${baseUrl}/internal/agent/mcp`), {
  requestInit: {
    headers: {
      'X-Agent-Internal-Token': internalToken,
      'X-User-Id': String(userId),
    },
  },
});

try {
  await client.connect(transport);
  const tools = await client.listTools();
  const names = (tools.tools || []).map((tool) => tool.name).sort();
  if (!names.includes('book.search') || !names.includes('knowledge_graph.query')) {
    throw new Error('Required read-only tools were not discovered.');
  }
  const result = await client.callTool({ name: 'book.search', arguments: { query: searchQuery } });
  const content = Array.isArray(result?.content) ? result.content : [];
  console.log(JSON.stringify({
    passed: true,
    protocol: client.getServerCapabilities ? 'streamable-http' : 'streamable-http',
    toolCount: names.length,
    requiredTools: names.filter((name) => ['book.search', 'knowledge_graph.query'].includes(name)),
    searchResultContentBlocks: content.length,
  }));
} finally {
  await client.close();
}
