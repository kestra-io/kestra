import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ErrorToastContainer from '../../../src/components/ErrorToastContainer.vue';
import { useFlowStore } from '../../../src/stores/flow';
import * as Markdown from '../../../src/utils/markdown';

// Mock the markdown module
vi.mock('../../../src/utils/markdown', () => ({
    render: vi.fn((text) => Promise.resolve(`<p>${text}</p>`))
}));

// Mock vue-router
vi.mock('vue-router', () => ({
    useRoute: vi.fn(() => ({
        name: 'flows/update'
    }))
}));

describe('ErrorToastContainer', () => {
    beforeEach(() => {
        setActivePinia(createPinia());
        vi.clearAllMocks();
    });

    it('renders error message when items array is empty', async () => {
        const message = {
            message: 'Test error message',
            content: {
                message: 'Content message'
            }
        };

        const wrapper = mount(ErrorToastContainer, {
            props: {
                message,
                items: []
            },
            global: {
                mocks: {
                    $t: (key: string) => key
                }
            }
        });

        // Wait for markdown to render
        await new Promise(resolve => setTimeout(resolve, 10));
        await wrapper.vm.$nextTick();

        expect(Markdown.render).toHaveBeenCalledWith('Test error message', { html: true });
    });

    it('renders error items when items array has elements', async () => {
        const message = {
            message: 'Test error message',
            content: {
                message: 'Content message'
            }
        };

        const items = [
            { path: 'task.id', message: 'Error at task' },
            { message: 'General error' }
        ];

        const wrapper = mount(ErrorToastContainer, {
            props: {
                message,
                items
            },
            global: {
                mocks: {
                    $t: (key: string) => key
                }
            }
        });

        await wrapper.vm.$nextTick();

        // Check that items are rendered
        const listItems = wrapper.findAll('li');
        expect(listItems).toHaveLength(2);
        expect(listItems[0].text()).toContain('At task.id');
        expect(listItems[0].text()).toContain('Error at task');
        expect(listItems[1].text()).toContain('General error');
    });

    it('renders 503 error message correctly', async () => {
        const message = {
            message: 'Server error',
            content: {
                message: 'Content message'
            },
            response: {
                status: 503
            }
        };

        const wrapper = mount(ErrorToastContainer, {
            props: {
                message,
                items: []
            },
            global: {
                mocks: {
                    $t: (key: string) => key
                }
            }
        });

        await new Promise(resolve => setTimeout(resolve, 10));
        await wrapper.vm.$nextTick();

        expect(Markdown.render).toHaveBeenCalledWith(
            'Server is temporarily unavailable. Please try again later.',
            { html: true }
        );
    });

    it('shows AI fix button in flow context', async () => {
        const message = {
            message: 'Test error message',
            content: {
                message: 'Content message'
            }
        };

        const wrapper = mount(ErrorToastContainer, {
            props: {
                message,
                items: []
            },
            global: {
                mocks: {
                    $t: (key: string) => key
                }
            }
        });

        await wrapper.vm.$nextTick();

        // Check for AI button
        const aiButton = wrapper.find('.slack-on-error');
        expect(aiButton.exists()).toBe(true);
    });

    it('calls onClose and opens AI copilot when fix with AI is clicked', async () => {
        const onCloseMock = vi.fn();
        const message = {
            message: 'Test error message',
            content: {
                message: 'Content message'
            }
        };

        const items = [
            { path: 'task.id', message: 'Error at task' }
        ];

        const wrapper = mount(ErrorToastContainer, {
            props: {
                message,
                items,
                onClose: onCloseMock
            },
            global: {
                mocks: {
                    $t: (key: string) => key
                }
            }
        });

        await wrapper.vm.$nextTick();

        // Mock sessionStorage
        const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');

        const flowStore = useFlowStore();
        const setOpenAiCopilotSpy = vi.spyOn(flowStore, 'setOpenAiCopilot');

        // Click the AI button
        const aiButton = wrapper.find('.slack-on-error');
        await aiButton.trigger('click');

        expect(onCloseMock).toHaveBeenCalled();
        expect(setItemSpy).toHaveBeenCalledWith(
            'kestra-ai-prompt',
            expect.stringContaining('Fix the following error in the flow')
        );
        expect(setOpenAiCopilotSpy).toHaveBeenCalledWith(true);

        setItemSpy.mockRestore();
    });

    it('updates markdown when message prop changes', async () => {
        const message = {
            message: 'Initial message',
            content: {
                message: 'Content message'
            }
        };

        const wrapper = mount(ErrorToastContainer, {
            props: {
                message,
                items: []
            },
            global: {
                mocks: {
                    $t: (key: string) => key
                }
            }
        });

        await new Promise(resolve => setTimeout(resolve, 10));
        await wrapper.vm.$nextTick();

        expect(Markdown.render).toHaveBeenCalledWith('Initial message', { html: true });

        // Update the message prop
        await wrapper.setProps({
            message: {
                message: 'Updated message',
                content: {
                    message: 'New content'
                }
            }
        });

        await new Promise(resolve => setTimeout(resolve, 10));
        await wrapper.vm.$nextTick();

        expect(Markdown.render).toHaveBeenCalledWith('Updated message', { html: true });
    });

    it('handles sessionStorage errors gracefully', async () => {
        const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
        const setItemMock = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new Error('Storage error');
        });

        const message = {
            message: 'Test error message',
            content: {
                message: 'Content message'
            }
        };

        const wrapper = mount(ErrorToastContainer, {
            props: {
                message,
                items: []
            },
            global: {
                mocks: {
                    $t: (key: string) => key
                }
            }
        });

        await wrapper.vm.$nextTick();

        // Click the AI button
        const aiButton = wrapper.find('.slack-on-error');
        await aiButton.trigger('click');

        expect(consoleWarnSpy).toHaveBeenCalledWith(
            'AI prompt not persisted to sessionStorage:',
            expect.any(Error)
        );

        consoleWarnSpy.mockRestore();
        setItemMock.mockRestore();
    });
});

